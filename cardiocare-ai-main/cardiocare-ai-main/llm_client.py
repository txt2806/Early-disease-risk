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
import re
import logging
from typing import Optional

try:
    from google import genai
    from google.genai import types
    from google.genai import errors as genai_errors
    _GENAI_AVAILABLE = True
except ImportError:
    _GENAI_AVAILABLE = False

_log = logging.getLogger("llm_client")

# Dùng Flash — model duy nhất còn free tier ổn định ở Gemini API
# (Pro đã bị loại khỏi free tier từ 04/2026). KHÔNG đổi sang *-pro
# nếu muốn giữ free tier.
DEFAULT_MODEL = "gemini-2.5-flash"

# [FIX v3] Số lần thử tối đa khi phát hiện câu trả lời bị cụt (1 lần gọi gốc
# + tối đa 2 lần thử lại = 3 lần tổng). Lần trước chỉ cho thử lại 1 lần
# (2 lần tổng) nhưng thực tế vẫn gặp trường hợp CẢ 2 lần đều bị cụt tương tự
# — tăng lên 3 lần để giảm khả năng này, đồng thời có phương án dự phòng
# cuối cùng (_clean_truncated_text) nếu vẫn cụt sau khi hết lượt thử.
MAX_ATTEMPTS = 3

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


def _looks_truncated(text: str) -> bool:
    """
    Heuristic phát hiện câu trả lời bị cụt giữa chừng KỂ CẢ KHI finish_reason
    =STOP (tức Gemini "tự quyết định" dừng, không phải do hết token).

    Chỉ dùng các tín hiệu ĐÁNG TIN CẬY, tránh báo nhầm câu trả lời hợp lệ:
    - Số dấu ** (markdown bold) là số LẺ -> đang giữa chừng in đậm thì dừng.
    - Số dấu "(" khác số dấu ")" -> câu/số liệu chưa đóng ngoặc.
    - Kết thúc bằng dấu phẩy -> câu chưa hết ý.
    """
    if not text:
        return False
    stripped = text.rstrip()
    if not stripped:
        return False

    if stripped.count("**") % 2 != 0:
        return True
    if stripped.count("(") != stripped.count(")"):
        return True
    if stripped[-1] == ",":
        return True

    return False


def _clean_truncated_text(text: str) -> str:
    """
    [FIX v3] Phương án dự phòng CUỐI CÙNG: nếu đã thử lại MAX_ATTEMPTS lần
    mà vẫn bị cụt, KHÔNG trả về markdown vỡ dở cho bác sĩ xem (trông như lỗi
    hệ thống). Thay vào đó, cắt gọn về câu HOÀN CHỈNH gần nhất và thêm ghi
    chú rõ ràng — người dùng vẫn đọc được nội dung có ý nghĩa, chỉ là ngắn
    hơn dự kiến, thay vì thấy "...(0.1" lửng lơ như trước.
    """
    stripped = text.rstrip()

    # Xoá dấu ** hở cuối cùng (in đậm chưa đóng) nếu có
    if stripped.count("**") % 2 != 0:
        idx = stripped.rfind("**")
        stripped = stripped[:idx].rstrip()

    # Cắt về câu hoàn chỉnh gần nhất (kết thúc bằng . ! ? : hoặc xuống dòng)
    matches = list(re.finditer(r"[.!?:]\s|\n", stripped))
    if matches:
        stripped = stripped[:matches[-1].end()].rstrip()

    if not stripped:
        # Trường hợp cực hiếm: cắt xong không còn gì — trả về câu xin lỗi
        # thay vì chuỗi rỗng gây lỗi ở endpoint (response.text rỗng cũng
        # bị coi là lỗi).
        return ("Xin lỗi bác sĩ, tôi gặp khó khăn khi tạo câu trả lời cho "
                "câu hỏi này. Vui lòng thử diễn đạt lại theo cách ngắn gọn hơn.")

    return stripped + ("\n\n*(Câu trả lời đã được rút gọn do model gặp khó khăn "
                        "khi tạo nội dung dài — vui lòng hỏi cụ thể hơn nếu cần "
                        "thêm chi tiết.)*")


def chat_completion(
    system_prompt: str,
    history: list[dict],
    user_message: str,
    model: str = DEFAULT_MODEL,
    max_output_tokens: int = 2048,
    temperature: float = 0.4,
    _attempt: int = 1,
) -> str:
    """
    Gọi Gemini API với system prompt + lịch sử hội thoại + câu hỏi mới.

    history: list các dict dạng {"role": "user"|"model", "text": "..."}
             (lịch sử hội thoại trước đó, có thể rỗng nếu là tin đầu tiên)

    Trả về: chuỗi câu trả lời từ model.
    Raise ChatError nếu gọi API thất bại (timeout, lỗi key, hết quota...).

    [FIX v1] max_output_tokens 512 -> 2048, phát hiện finish_reason=
    MAX_TOKENS để raise lỗi rõ ràng thay vì trả về câu cụt.

    [FIX v2] Phát hiện thêm: Gemini có thể tự dừng SỚM với finish_reason=
    STOP (không phải MAX_TOKENS) nhưng nội dung vẫn bị cụt giữa chừng.
    Dùng heuristic _looks_truncated() để bắt cả trường hợp này.

    [FIX v3] 2 cải tiến sau khi phát hiện 1 lần thử lại vẫn CHƯA ĐỦ (có
    trường hợp cả 2 lần gọi đều bị cụt tương tự với câu hỏi cấu trúc phức
    tạp/liệt kê):
    - thinking_budget: đổi từ 0 (tắt hẳn) sang 512 — tắt hoàn toàn có thể
      khiến model (đặc biệt bản Flash, nhỏ/nhanh) không có "không gian" để
      lên kế hoạch cho câu trả lời có cấu trúc (danh sách, nhiều mục), dễ
      bị rối và dừng giữa chừng hơn. 512 token thinking + 2048 max_output
      là điểm cân bằng hợp lý giữa 2 rủi ro (hết ngân sách vì thinking vs.
      rối do không có thinking).
    - Tăng số lần thử từ 1 lên MAX_ATTEMPTS-1=2 lần thử lại (3 lần tổng),
      và nếu vẫn cụt sau khi hết lượt thử, dùng _clean_truncated_text() để
      cắt gọn về câu hoàn chỉnh thay vì trả markdown vỡ cho người dùng.
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
                thinking_config=types.ThinkingConfig(thinking_budget=512),
            ),
        )
    except genai_errors.APIError as e:
        raise ChatError(f"Lỗi từ Gemini API: {e}") from e
    except Exception as e:
        raise ChatError(f"Lỗi kết nối khi gọi Gemini API: {e}") from e

    finish_reason = None
    if response.candidates:
        finish_reason = response.candidates[0].finish_reason

    _log.info(f"Gemini finish_reason={finish_reason} | attempt={_attempt}/{MAX_ATTEMPTS} | "
              f"reply_len={len(response.text or '')}")

    if finish_reason == types.FinishReason.MAX_TOKENS:
        raise ChatError(
            "Câu trả lời bị cắt do vượt giới hạn độ dài (max_output_tokens="
            f"{max_output_tokens}). Vui lòng thử lại với câu hỏi ngắn gọn "
            "hơn, hoặc chia thành nhiều câu hỏi nhỏ."
        )

    if not response.text:
        raise ChatError("Gemini API trả về phản hồi rỗng.")

    if _looks_truncated(response.text):
        if _attempt < MAX_ATTEMPTS:
            _log.warning(
                f"Phát hiện câu trả lời bị cụt (lần {_attempt}/{MAX_ATTEMPTS}, "
                f"finish_reason=STOP nhưng nội dung chưa hoàn chỉnh) — thử lại. "
                f"Đoạn cuối: ...{response.text[-60:]!r}"
            )
            return chat_completion(
                system_prompt=system_prompt,
                history=history,
                user_message=user_message,
                model=model,
                max_output_tokens=max_output_tokens,
                temperature=max(0.1, temperature - 0.15 * _attempt),
                _attempt=_attempt + 1,
            )
        # [FIX v3] Đã hết lượt thử mà vẫn cụt — dùng phương án dự phòng
        # thay vì trả markdown vỡ cho người dùng.
        _log.warning(
            f"Vẫn bị cụt sau {MAX_ATTEMPTS} lần thử — dùng _clean_truncated_text() "
            f"để cắt gọn về câu hoàn chỉnh thay vì trả nội dung vỡ."
        )
        return _clean_truncated_text(response.text)

    return response.text