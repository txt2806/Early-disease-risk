"""
llm_client.py — Module gọi Gemini API dùng chung cho chatbot
══════════════════════════════════════════════════════════════
Dùng chung cho /chat/doctor và /chat/patient, nhưng module này
KHÔNG chứa logic riêng cho từng role — chỉ là wrapper gọi API.
System prompt khác nhau được truyền vào từ mỗi endpoint riêng.

Cài đặt:
    pip install google-genai --break-system-packages

Lấy API key tại: https://aistudio.google.com (miễn phí, không cần thẻ)
Đặt biến môi trường:
    export GEMINI_API_KEY="your-api-key-here"

⚠️  LƯU Ý BẢO MẬT (đọc kỹ trước khi dùng với dữ liệu thật):
Ở free tier, Google có thể dùng prompt/response để cải thiện model
của họ. KHÔNG gửi dữ liệu bệnh nhân THẬT (tên, số CCCD, địa chỉ...)
qua free tier. Với đồ án dùng dữ liệu demo/giả lập thì không vấn đề.
Nếu deploy thật cho bệnh nhân thật, cần bật billing (trả phí) để có
điều khoản bảo mật dữ liệu tốt hơn, hoặc ẩn danh hoàn toàn dữ liệu
trước khi gửi (không gửi tên thật, chỉ gửi chỉ số y tế).
"""

import os
from typing import Optional

try:
    from google import genai
    from google.genai import types
    from google.genai import errors as genai_errors
    _GENAI_AVAILABLE = True
except ImportError:
    _GENAI_AVAILABLE = False


# Dùng Flash — model duy nhất còn free tier ổn định ở Gemini API
# (Pro đã bị loại khỏi free tier từ 04/2026). KHÔNG đổi sang *-pro
# nếu muốn giữ free tier.
DEFAULT_MODEL = os.environ.get("GEMINI_MODEL", "gemini-3.5-flash")

_client = None


def get_client():
    global _client
    if not _GENAI_AVAILABLE:
        raise RuntimeError(
            "Chưa cài thư viện google-genai. Cài bằng: "
            "pip install google-genai --break-system-packages"
        )
    if _client is None:
        api_key = os.environ.get("GEMINI_API_KEY")
        if not api_key:
            raise RuntimeError(
                "Thiếu biến môi trường GEMINI_API_KEY. "
                "Lấy key tại https://aistudio.google.com rồi chạy: "
                "export GEMINI_API_KEY=\"...\" trước khi khởi động server."
            )
        _client = genai.Client(api_key=api_key)
    return _client


class ChatError(Exception):
    """Lỗi khi gọi LLM — endpoint nên bắt và trả message thân thiện cho người dùng."""
    pass


def chat_completion(
    system_prompt: str,
    history: list[dict],
    user_message: str,
    model: str = DEFAULT_MODEL,
    max_output_tokens: int = 4096,
    temperature: float = 0.4,
) -> str:
    """
    Gọi Gemini API với system prompt + lịch sử hội thoại + câu hỏi mới.

    history: list các dict dạng {"role": "user"|"model", "text": "..."}
             (lịch sử hội thoại trước đó, có thể rỗng nếu là tin đầu tiên)

    Trả về: chuỗi câu trả lời từ model.
    Raise ChatError nếu gọi API thất bại (timeout, lỗi key, hết quota...).
    """
    client = get_client()

    contents = []
    for turn in history:
        contents.append(
            types.Content(
                role=turn["role"],
                parts=[types.Part.from_text(text=turn["text"])],
            )
        )
    contents.append(
        types.Content(role="user", parts=[types.Part.from_text(text=user_message)])
    )

    try:
        response = client.models.generate_content(
            model=model,
            contents=contents,
            config=types.GenerateContentConfig(
                system_instruction=system_prompt,
                max_output_tokens=max_output_tokens,
                temperature=temperature,
            ),
        )
    except genai_errors.APIError as e:
        # Lỗi từ phía Gemini API: hết quota (429), key sai, model không hợp lệ...
        raise ChatError(f"Lỗi từ Gemini API: {e}") from e
    except Exception as e:
        # Lỗi khác (timeout, mất kết nối mạng...) — vẫn quy về ChatError để
        # endpoint xử lý nhất quán, không để lộ traceback nội bộ.
        raise ChatError(f"Lỗi kết nối khi gọi Gemini API: {e}") from e

    if not response.text:
        raise ChatError("Gemini API trả về phản hồi rỗng.")

    return response.text
