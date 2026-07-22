"""
FastAPI Server v2 - Nâng cấp với SHAP + Trend Features
- Giải thích kết quả AI bằng ngôn ngữ tự nhiên (SHAP)
- Phân tích xu hướng theo thời gian (Trend Features)
Chạy: uvicorn api_v2:app --reload --host 0.0.0.0 --port 8000
"""

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import JSONResponse, FileResponse
from pydantic import BaseModel
from typing import Optional, List
import pickle
import pandas as pd
import numpy as np
import warnings
import os
from pathlib import Path
warnings.filterwarnings("ignore")
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
try:
    from ai_logger import get_logger, log_api_request, SESSION_LOG, SUMMARY_FILE
    _api_logger = get_logger("api_v2")
    _API_LOGGING = True
except ImportError:
    import logging
    _api_logger = logging.getLogger("api_v2")
    _API_LOGGING = False
    def log_api_request(logger, endpoint, **fields):
        # Fallback im lặng nếu ai_logger.py không có sẵn (vd. thiếu file
        # khi deploy) — tránh crash server chỉ vì thiếu log.
        pass

try:
    from llm_client import chat_completion, ChatError
    _CHATBOT_AVAILABLE = True
except ImportError:
    _CHATBOT_AVAILABLE = False
    class ChatError(Exception):
        pass
    def chat_completion(*args, **kwargs):
        raise ChatError(
            "Chưa cài thư viện google-genai hoặc thiếu file llm_client.py. "
            "Cài bằng: pip install google-genai --break-system-packages"
        )


app = FastAPI(title="CardioCare AI API v2")

# ─── Load model ──────────────────────────────────────────────
# [A.6] KHẮC PHỤC: đường dẫn tuyệt đối Windows cứng (C:\Users\NhatMinh\...)
# sẽ không chạy được trên máy khác hay server Linux khi deploy thật.
#
# Thứ tự ưu tiên:
#   1. Biến môi trường MODEL_PATH (đặt khi deploy: export MODEL_PATH=/path/to/model.pkl)
#   2. File heart_model_tuned.pkl nằm CÙNG THƯ MỤC với chính api_v2.py này
#      (mặc định tiện cho việc chạy local như hiện tại, chỉ cần đặt 2 file
#      cùng chỗ, không phải sửa code mỗi khi đổi máy/đổi user Windows).
_DEFAULT_MODEL_PATH = Path(__file__).resolve().parent / "heart_model_tuned.pkl"
MODEL_PATH = os.environ.get("MODEL_PATH", str(_DEFAULT_MODEL_PATH))

try:
    with open(MODEL_PATH, "rb") as f:
        artifacts = pickle.load(f)
    model          = artifacts["model"]
    scaler         = artifacts["scaler"]
    imputer        = artifacts["imputer"]
    label_encoders = artifacts["label_encoders"]
    feature_names  = artifacts["feature_names"]
    metrics        = artifacts.get("metrics", {})
    print(f"✅ Model v1 loaded từ '{MODEL_PATH}' | AUC: {metrics.get('auc', 'N/A')}")
except Exception as e:
    print(f"❌ Lỗi load model v1 tại '{MODEL_PATH}': {e}")
    model = None
    artifacts = {}
    metrics = {}
    feature_names = []

# [D.14] Load thêm model v2 (multi-class 4 lớp) song song với v1
# Giữ v1 để backward compat với /predict và Spring Boot hiện tại.
_DEFAULT_MODEL_V2_PATH = Path(__file__).resolve().parent / "heart_model_tuned_v3.pkl"
MODEL_V2_PATH = os.environ.get("MODEL_V2_PATH", str(_DEFAULT_MODEL_V2_PATH))

try:
    with open(MODEL_V2_PATH, "rb") as f:
        artifacts_v2 = pickle.load(f)
    model_v2          = artifacts_v2["model"]
    scaler_v2         = artifacts_v2["scaler"]
    imputer_v2        = artifacts_v2["imputer"]
    label_encoders_v2 = artifacts_v2["label_encoders"]
    feature_names_v2  = artifacts_v2["feature_names"]
    class_mapping_v2  = artifacts_v2["class_mapping"]   # {0:"NO_DISEASE", 1:"MILD",...}
    class_names_v2    = artifacts_v2["class_names"]
    metrics_v2        = artifacts_v2.get("metrics", {})
    print(f"✅ Model v2 loaded từ '{MODEL_V2_PATH}' | "
          f"BalAcc: {metrics_v2.get('balanced_accuracy', 'N/A'):.4f}")
except Exception as e:
    print(f"⚠️  Model v2 chưa có hoặc lỗi load tại '{MODEL_V2_PATH}': {e}")
    print("   → Chạy tune_model_v2.py để tạo file này trước khi dùng /predict/v2")
    model_v2 = None
    artifacts_v2 = {}
    metrics_v2 = {}
    class_mapping_v2 = {}
    class_names_v2 = []


def ensure_model_ready():
    """
    [A.6] Chặn sớm và báo lỗi RÕ RÀNG khi model chưa load được, thay vì để
    endpoint crash với AttributeError mơ hồ ('NoneType' object has no
    attribute 'predict_proba') — lỗi đó không nói cho Spring Boot/bác sĩ
    biết nguyên nhân thật là model load thất bại lúc khởi động server.
    """
    if model is None:
        raise HTTPException(
            status_code=503,
            detail=f"AI model chưa sẵn sàng (load thất bại lúc khởi động server). "
                    f"Kiểm tra lại đường dẫn MODEL_PATH='{MODEL_PATH}' và file .pkl có tồn tại không."
        )

# Load SHAP explainer (lazy load)
_explainer = None
_shap_error = None  # [FIX] lưu lý do thật khi SHAP không khởi tạo được,
                     # thay vì nuốt lỗi im lặng (except ImportError: pass)
                     # khiến trước đây không biết vì sao "SHAP không khả dụng"
                     # khi đổi sang server/môi trường khác.
def get_explainer():
    global _explainer, _shap_error
    if _explainer is None and model is not None and _shap_error is None:
        try:
            import shap
        except ImportError as e:
            _shap_error = (
                f"Chưa cài thư viện shap trên môi trường đang chạy server này "
                f"({e}). Cài bằng: pip install shap --break-system-packages"
            )
            print(f"⚠️  {_shap_error}")
            return None
        try:
            _explainer = shap.TreeExplainer(model)
        except Exception as e:
            # Lỗi KHÔNG phải do thiếu thư viện (vd. model không tương thích
            # TreeExplainer, xung đột phiên bản shap/sklearn/xgboost...).
            # Trước đây lỗi này sẽ KHÔNG bị bắt và làm crash luôn cả request
            # /predict — giờ bắt lại để trả "SHAP không khả dụng" một cách
            # an toàn, đồng thời log rõ nguyên nhân thật.
            _shap_error = f"Lỗi khởi tạo SHAP TreeExplainer: {type(e).__name__}: {e}"
            print(f"⚠️  {_shap_error}")
            return None
    return _explainer

# Tên tiếng Việt
FEATURE_LABELS = {
    "age"     : "Tuổi",
    "sex"     : "Giới tính",
    "cp"      : "Loại đau ngực",
    "trestbps": "Huyết áp",
    "chol"    : "Cholesterol",
    "fbs"     : "Đường huyết cao",
    "restecg" : "Kết quả ECG",
    "thalch"  : "Nhịp tim tối đa",
    "exang"   : "Đau ngực khi gắng sức",
    "oldpeak" : "ST Depression",
    "slope"   : "Độ dốc ST",
    "ca"      : "Số mạch vành tắc",
    "thal"    : "Thalassemia",
}

# ─── Schema ──────────────────────────────────────────────────
class PatientData(BaseModel):
    age       : float
    sex       : str
    cp        : str
    trestbps  : Optional[float] = None
    chol      : Optional[float] = None
    fbs       : Optional[str]   = None
    restecg   : Optional[str]   = None
    thalch    : Optional[float] = None
    exang     : Optional[str]   = None
    oldpeak   : Optional[float] = None
    slope     : Optional[str]   = None
    ca        : Optional[float] = None
    thal      : Optional[str]   = None

class TrendData(BaseModel):
    """Dữ liệu nhiều lần đo để phân tích xu hướng"""
    visits: List[PatientData]   # Danh sách các lần khám (cũ → mới)

# ─── Helper ──────────────────────────────────────────────────
class InvalidCategoricalValueError(Exception):
    """
    Raise khi giá trị phân loại gửi lên KHÔNG khớp với bất kỳ nhãn nào
    mà LabelEncoder đã học lúc train (heart_model_tuned.pkl).

    Đây là lỗi CỐ Ý không tự sửa, vì việc tự "đoán" giá trị đúng (fallback
    âm thầm về known[0]) đã từng gây sai lệch kết quả AI tới 8 điểm % xác
    suất mà không ai biết — nguy hiểm cho một hệ thống hỗ trợ chẩn đoán
    y tế. Thà báo lỗi rõ ràng để bác sĩ/Spring Boot sửa dữ liệu gửi lên.
    """
    def __init__(self, field: str, received, allowed: list):
        self.field = field
        self.received = received
        self.allowed = allowed
        super().__init__(
            f"Giá trị '{received}' cho trường '{field}' không hợp lệ. "
            f"Các giá trị được chấp nhận: {allowed}"
        )


@app.exception_handler(InvalidCategoricalValueError)
async def invalid_categorical_value_handler(request, exc: InvalidCategoricalValueError):
    """
    Trả lỗi 422 RÕ RÀNG cho Spring Boot/bác sĩ thay vì âm thầm tính ra một
    xác suất sai. Spring Boot nên hiển thị thông báo này cho bác sĩ và/hoặc
    log lại để dev kiểm tra nguồn dữ liệu gửi lên (form, mapping cũ...).
    """
    # In ra terminal uvicorn để dev thấy NGAY field nào sai khi đang chạy local,
    # không phải mò trong log "422 Unprocessable Content" trống trơn.
    print(f"⚠️  [422] Dữ liệu không hợp lệ — field='{exc.field}' "
          f"received='{exc.received}' allowed={exc.allowed}")
    return JSONResponse(
        status_code=422,
        content={
            "error": "INVALID_CATEGORICAL_VALUE",
            "field": exc.field,
            "received_value": exc.received,
            "allowed_values": exc.allowed,
            "message": str(exc),
        },
    )


def preprocess(data: dict) -> np.ndarray:
    sample = pd.DataFrame([data])

    for col, le in label_encoders.items():
        if col not in sample.columns:
            continue

        raw_value = sample[col].iloc[0]

        # Giá trị thiếu (None/NaN) hợp lệ CHỈ KHI encoder của cột đó từng
        # thấy NaN lúc train (tức cột này cho phép optional). Nếu cột bắt
        # buộc (vd 'sex', 'cp') mà bị thiếu, đây là lỗi dữ liệu đầu vào,
        # không phải lỗi encode — báo rõ thay vì để model chạy với rác.
        if raw_value is None or (isinstance(raw_value, float) and np.isnan(raw_value)):
            known_has_nan = any(
                isinstance(c, float) and np.isnan(c) for c in le.classes_
            )
            if known_has_nan:
                continue  # để SimpleImputer xử lý ở bước sau
            else:
                raise InvalidCategoricalValueError(
                    field=col, received=None,
                    allowed=[c for c in le.classes_ if not (isinstance(c, float) and np.isnan(c))]
                )

        sample[col] = sample[col].astype(str)
        known = [str(c) for c in le.classes_ if not (isinstance(c, float) and np.isnan(c))]

        value_str = sample[col].iloc[0]
        if value_str not in known:
            raise InvalidCategoricalValueError(field=col, received=value_str, allowed=known)

        sample[col] = le.transform(sample[col])

    sample = sample[feature_names]
    return scaler.transform(imputer.transform(sample))

# ─── Giải thích lâm sàng riêng cho Loại đau ngực ───────────────
# Dùng giá trị thực tế (cp) thay vì chỉ dựa vào dấu SHAP,
# vì "asymptomatic" trong dataset UCI lại thường là ca nặng hơn.
CP_CLINICAL_NOTES = {
    "typical angina":  "Đau thắt ngực điển hình — triệu chứng đau ngực do thiếu máu cơ tim khi gắng sức, giảm khi nghỉ.",
    "atypical angina": "Đau thắt ngực không điển hình — triệu chứng không hoàn toàn khớp mẫu đau thắt ngực cổ điển.",
    "non-anginal":     "Đau ngực không do tim — ít liên quan đến bệnh động mạch vành.",
    "asymptomatic":    "Không có triệu chứng đau ngực — nhóm này trong dữ liệu lâm sàng thực tế lại thường gặp ở bệnh nhân có bệnh tim nặng (đặc biệt người tiểu đường, người già) do giảm cảm nhận đau, cần đặc biệt lưu ý không chủ quan.",
}

# ─── Giải thích lâm sàng cho các feature còn lại (theo dấu SHAP up/down) ───
CLINICAL_EXPLANATIONS = {
    "Số mạch vành tắc": {
        "up":   "Có {val} mạch vành bị tắc hẹp — chỉ số cực kỳ quan trọng trong chẩn đoán bệnh tim.",
        "down": "Không có mạch vành bị tắc, dấu hiệu tốt về cấu trúc tim mạch."
    },
    "Thalassemia": {
        "up":   "Khiếm khuyết tưới máu hồi phục (reversible defect) gợi ý thiếu máu cơ tim.",
        "down": "Kết quả Thalassemia bình thường, không ghi nhận khiếm khuyết tưới máu."
    },
    "ST Depression": {
        "up":   "ST Depression {val:.1f} mm trong khi gắng sức — dấu hiệu thiếu máu cơ tim cục bộ (AHA Class II).",
        "down": "ST Depression thấp, không ghi nhận thiếu máu cơ tim khi gắng sức."
    },
    "Tuổi": {
        "up":   "Tuổi {val:.0f} — nguy cơ bệnh tim mạch tăng theo tuổi (ACC/AHA 2023 Guidelines).",
        "down": "Tuổi còn trẻ, giảm yếu tố nguy cơ tim mạch tự nhiên."
    },
    "Giới tính": {
        "up":   "Nam giới có nguy cơ bệnh tim mạch vành sớm hơn nữ trung bình 10 năm.",
        "down": "Nữ giới trước mãn kinh có lợi thế bảo vệ tim mạch từ estrogen."
    },
    "Đau ngực khi gắng sức": {
        "up":   "Có đau ngực khi gắng sức — triệu chứng điển hình của thiếu máu cơ tim khi vận động.",
        "down": "Không có đau ngực khi gắng sức — dấu hiệu tốt về dự trữ tim mạch."
    },
    "Cholesterol": {
        "up":   "Cholesterol {val:.0f} mg/dL vượt ngưỡng khuyến nghị (<200 mg/dL) theo AHA.",
        "down": "Cholesterol trong giới hạn an toàn."
    },
    "Huyết áp": {
        "up":   "Huyết áp lúc nghỉ {val:.0f} mmHg — cao huyết áp làm tăng tải cơ tim.",
        "down": "Huyết áp ổn định, không ghi nhận tăng áp lực tim."
    },
    "Độ dốc ST": {
        "up":   "Độ dốc ST dốc xuống (downsloping) hoặc phẳng (flat) — dấu hiệu cổ điển của thiếu máu cơ tim theo ACC/AHA.",
        "down": "Độ dốc ST dốc lên (upsloping) — ít liên quan đến thiếu máu cơ tim, tiên lượng tốt hơn."
    },
    "Kết quả ECG": {
        "up":   "Phì đại thất trái hoặc bất thường ST-T trên ECG — dấu hiệu bệnh tim cấu trúc.",
        "down": "ECG bình thường lúc nghỉ, giảm khả năng bệnh tim cấu trúc nặng."
    },
    "Đường huyết cao": {
        "up":   "Đường huyết lúc đói >120 mg/dL — đái tháo đường là yếu tố nguy cơ tim mạch độc lập (AHA 2023).",
        "down": "Đường huyết bình thường, không ghi nhận đái tháo đường."
    },
}


def get_thalch_clinical_text(val, direction_up: bool, age: float = None) -> str:
    """
    Giải thích nhịp tim tối đa (thalch) theo dải giá trị thực tế,
    so với nhịp tim tối đa lý thuyết theo tuổi (HRmax ước tính = 220 - tuổi).

    Lưu ý lâm sàng: thalch THẤP thường là dấu hiệu XẤU (tim không đáp ứng
    tốt khi gắng sức), còn thalch CAO (trong giới hạn sinh lý) thường là
    dấu hiệu TỐT. Vì vậy không dùng mẫu up/down chung như các feature khác.
    """
    if val is None:
        return ""

    # Nhịp tim tối đa lý thuyết theo tuổi; nếu thiếu tuổi, dùng mốc tham
    # chiếu trung bình 200 bpm để không gây hiểu nhầm là đã hiệu chỉnh theo tuổi
    if age:
        hr_max_theo = 220 - age
        age_note = f"so với mức lý thuyết theo tuổi (~{hr_max_theo:.0f} bpm)"
    else:
        hr_max_theo = 200
        age_note = "so với mốc tham chiếu trung bình (~200 bpm, do thiếu dữ liệu tuổi)"

    pct_of_max = (val / hr_max_theo * 100) if hr_max_theo > 0 else 0

    if val > 220:
        return (
            f"Nhịp tim tối đa ghi nhận {val:.0f} bpm — CAO BẤT THƯỜNG, vượt giới hạn sinh lý "
            f"tối đa của con người. Nhiều khả năng đây là sai số khi đo hoặc nhập liệu, "
            f"cần kiểm tra lại số liệu trước khi đưa vào chẩn đoán."
        )
    elif val >= hr_max_theo:
        return (
            f"Nhịp tim tối đa {val:.0f} bpm khi gắng sức — đạt hoặc vượt mức lý thuyết theo tuổi "
            f"({age_note}). Đây là dấu hiệu tốt, cho thấy tim đáp ứng đầy đủ với gắng sức, "
            f"nhưng nếu vượt quá nhiều cũng nên xem lại độ chính xác của phép đo."
        )
    elif pct_of_max >= 85:
        return (
            f"Nhịp tim tối đa {val:.0f} bpm khi gắng sức — đạt khoảng {pct_of_max:.0f}% mức lý thuyết "
            f"theo tuổi ({age_note}). Mức này cho thấy tim còn đáp ứng tốt với tải, "
            f"là dấu hiệu tích cực về dự trữ tim mạch."
        )
    elif pct_of_max >= 70:
        return (
            f"Nhịp tim tối đa {val:.0f} bpm khi gắng sức — đạt khoảng {pct_of_max:.0f}% mức lý thuyết "
            f"theo tuổi ({age_note}), ở mức trung bình, đáp ứng tim mạch trong giới hạn bình thường."
        )
    else:
        return (
            f"Nhịp tim tối đa {val:.0f} bpm khi gắng sức — chỉ đạt khoảng {pct_of_max:.0f}% mức lý thuyết "
            f"theo tuổi ({age_note}), THẤP hơn dự kiến. Đây có thể là dấu hiệu tim đáp ứng kém khi "
            f"gắng sức (chronotropic incompetence — giảm khả năng tăng nhịp tim theo tải), "
            f"cần lưu ý đánh giá thêm."
        )


def get_shap_explanation(sample_scaled, original_data: dict = None):
    """Trả về giải thích SHAP học thuật cho bác sĩ"""
    explainer = get_explainer()
    if explainer is None:
        return {"explanation": "SHAP không khả dụng", "top_factors": [], "clinical_summary": ""}

    shap_vals = explainer.shap_values(sample_scaled)
    if hasattr(shap_vals, "ndim") and shap_vals.ndim == 3:
        sv_patient = shap_vals[:, :, 1]
    elif isinstance(shap_vals, list):
        sv_patient = shap_vals[1]
    else:
        sv_patient = shap_vals
    sv_patient = sv_patient.ravel()

    top3_idx = np.argsort(np.abs(sv_patient))[::-1][:3]
    factors = []
    clinical_points = []

    for idx in top3_idx:
        feat_key  = feature_names[idx]
        fname     = FEATURE_LABELS.get(feat_key, feat_key)
        direction = "↑ tăng" if sv_patient[idx] > 0 else "↓ giảm"
        impact    = abs(sv_patient[idx])

        # Giá trị gốc của bệnh nhân (trước khi encode)
        val = original_data.get(feat_key, 0) if original_data else 0

        # Xử lý đặc biệt cho "Loại đau ngực" — dùng giá trị thực tế,
        # KHÔNG dùng template up/down vì dễ gây hiểu sai về mặt lâm sàng
        if fname == "Loại đau ngực" and isinstance(val, str):
            clinical_text = CP_CLINICAL_NOTES.get(val, "")
        elif fname == "Nhịp tim tối đa":
            age_val = original_data.get("age") if original_data else None
            clinical_text = get_thalch_clinical_text(val, sv_patient[idx] > 0, age_val)
        else:
            up_or_down = "up" if sv_patient[idx] > 0 else "down"
            explanations = CLINICAL_EXPLANATIONS.get(fname, {})
            clinical_text = explanations.get(up_or_down, "")
            try:
                clinical_text = clinical_text.format(val=val)
            except Exception:
                pass

        factors.append({
            "feature":   fname,
            "direction": direction,
            "impact":    round(float(impact), 4),
            "clinical":  clinical_text,
            "value":     val
        })

        if clinical_text:
            clinical_points.append(f"• {fname}: {clinical_text}")

    # Tổng hợp giải thích học thuật
    risk_factors_str = ", ".join([f"{f['feature']} ({f['direction']} nguy cơ)" for f in factors])

    explanation = (
        f"Ba yếu tố ảnh hưởng lớn nhất: {risk_factors_str}. "
        f"Đánh giá dựa trên mô hình Random Forest (AUC=0.93) huấn luyện từ "
        f"bộ dữ liệu UCI Heart Disease 920 bệnh nhân."
    )

    clinical_summary = "\n".join(clinical_points)

    return {
        "explanation":      explanation,
        "top_factors":      factors,
        "clinical_summary": clinical_summary
    }

# ═══════════════════════════════════════════════════════════════
# [D.16] PHÂN TÍCH XU HƯỚNG DÙNG TOÀN BỘ LỊCH SỬ KHÁM
# Dùng chung cho cả model v1 (binary) và v2 (multiclass) — đây là hàm
# bị THIẾU trong bản trước, gây lỗi "NameError: name 'analyze_trend_
# full_history' is not defined" tại /predict/trend và /predict/v2/trend.
#
# Khác với cách cũ (chỉ so 2 lần khám gần nhất), hàm này dùng HỒI QUY
# TUYẾN TÍNH (linear regression) qua TOÀN BỘ chuỗi giá trị để bắt được
# xu hướng dài hạn — ví dụ 5 lần khám dao động lên xuống nhẹ ở 2 lần
# cuối nhưng xu hướng tổng thể vẫn tăng rõ rệt, cách so 2 lần cũ sẽ
# báo "ổn định" sai lệch, còn cách này vẫn phát hiện đúng.
# ═══════════════════════════════════════════════════════════════
def analyze_trend_full_history(values: list, model_type: str = "binary"):
    """
    [D.16] Phân tích xu hướng qua toàn bộ lịch sử (không chỉ 2 lần gần nhất).

    Args:
        values: danh sách giá trị liên tục theo thời gian (cũ → mới).
            - model_type="binary": đây là probs_raw (xác suất P(có bệnh), 0.0–1.0)
            - model_type="multiclass": đây là prob_severe_raw (P(SEVERE), 0.0–1.0)
        model_type: "binary" hoặc "multiclass" — chỉ ảnh hưởng đến NGÔN NGỮ
            hiển thị trong trend_msg (nói "nguy cơ" hay "mức độ nặng"),
            không ảnh hưởng đến công thức tính toán (cả 2 đều là xác suất
            liên tục 0.0–1.0 nên dùng chung 1 thuật toán hồi quy).

    Returns:
        tuple (trend, trend_msg, trend_detail):
          - trend: "INCREASING" / "DECREASING" / "STABLE" / "INSUFFICIENT_DATA" / "UNKNOWN"
          - trend_msg: thông báo tiếng Việt ngắn, hiển thị trực tiếp cho bác sĩ
          - trend_detail: dict chứa số liệu kỹ thuật (slope, r_squared, n_visits...)
            để debug hoặc hiển thị chi tiết hơn nếu cần (vd. trên ai-predict.html)
    """
    n = len(values)

    if n == 0:
        return "UNKNOWN", "Chưa có dữ liệu để phân tích xu hướng", {"n_visits": 0}

    if n == 1:
        return "UNKNOWN", "Chưa đủ dữ liệu lịch sử (chỉ có 1 lần khám)", {"n_visits": 1}

    subject = "nguy cơ" if model_type == "binary" else "mức độ nặng"

    if n == 2:
        # Quá ít điểm để hồi quy có ý nghĩa thống kê -> dùng so sánh
        # trực tiếp 2 lần gần nhất (logic đơn giản, vẫn đúng với n=2).
        prev_val, curr_val = values[-2], values[-1]
        abs_change = curr_val - prev_val
        abs_pct_str = f"{abs_change*100:+.1f} điểm %"

        if abs_change > 0.05:
            trend = "INCREASING"
            trend_msg = f"⚠️ {subject.capitalize()} tăng {abs_pct_str} so với lần trước"
        elif abs_change < -0.05:
            trend = "DECREASING"
            trend_msg = f"✅ {subject.capitalize()} giảm {abs_pct_str} so với lần trước"
        else:
            trend = "STABLE"
            trend_msg = f"{subject.capitalize()} ổn định (thay đổi {abs_pct_str})"

        return trend, trend_msg, {
            "n_visits": 2,
            "method": "two_point_comparison",
            "abs_change": round(float(abs_change), 4),
        }

    # n >= 3: hồi quy tuyến tính qua TOÀN BỘ lịch sử
    x = np.arange(n, dtype=float)
    y = np.array(values, dtype=float)

    slope, intercept = np.polyfit(x, y, 1)
    y_pred = slope * x + intercept
    ss_res = np.sum((y - y_pred) ** 2)
    ss_tot = np.sum((y - np.mean(y)) ** 2)
    r_squared = 1 - (ss_res / ss_tot) if ss_tot > 0 else 0.0

    # Ngưỡng: thay đổi trung bình > 2 điểm %/lần khám VÀ r_squared đủ cao
    # (>= 0.3) mới coi là xu hướng thật — r_squared thấp nghĩa là các
    # điểm dao động ngẫu nhiên, không theo chiều hướng rõ ràng nào, dù
    # slope tính ra có vẻ khác 0 đôi chút.
    SLOPE_THRESHOLD = 0.02
    total_change_pct = slope * (n - 1) * 100

    if abs(slope) < SLOPE_THRESHOLD or r_squared < 0.3:
        trend = "STABLE"
        trend_msg = (
            f"{subject.capitalize()} ổn định qua {n} lần khám — không có chiều "
            f"hướng tăng/giảm rõ rệt (độ phù hợp xu hướng R²={r_squared:.2f})."
        )
    elif slope > 0:
        trend = "INCREASING"
        trend_msg = (
            f"⚠️ {subject.capitalize()} có xu hướng TĂNG dần qua {n} lần khám, "
            f"thay đổi tổng cộng khoảng {total_change_pct:+.1f} điểm % từ lần đầu "
            f"đến lần gần nhất (R²={r_squared:.2f})."
        )
    else:
        trend = "DECREASING"
        trend_msg = (
            f"✅ {subject.capitalize()} có xu hướng GIẢM dần qua {n} lần khám, "
            f"thay đổi tổng cộng khoảng {total_change_pct:+.1f} điểm % từ lần đầu "
            f"đến lần gần nhất (R²={r_squared:.2f})."
        )

    trend_detail = {
        "n_visits": n,
        "method": "linear_regression",
        "slope_per_visit": round(float(slope), 5),
        "r_squared": round(float(r_squared), 4),
        "total_change_pct": round(float(total_change_pct), 2),
    }

    return trend, trend_msg, trend_detail


# ─── Endpoints ───────────────────────────────────────────────
@app.post("/predict")
def predict(data: PatientData):
    ensure_model_ready()
    data_dict = data.dict()
    sample_scaled = preprocess(data_dict)
    prob       = float(model.predict_proba(sample_scaled)[0][1])
    prediction = int(model.predict(sample_scaled)[0])

    if prob >= 0.7:
        risk_level = "HIGH"
        message    = "Nguy cơ cao — cần khám chuyên khoa tim mạch ngay"
    elif prob >= 0.4:
        risk_level = "MEDIUM"
        message    = "Nguy cơ trung bình — nên theo dõi và kiểm tra định kỳ"
    else:
        risk_level = "LOW"
        message    = "Nguy cơ thấp — duy trì lối sống lành mạnh"

    shap_result = get_shap_explanation(sample_scaled, data_dict)

    # [D.15][D.17] Áp dụng cho cả model v1, không chỉ riêng v2 — bệnh
    # nhân dưới 35 tuổi hoặc có chỉ số ngoài khoảng sinh lý cần được
    # cảnh báo dù dùng model nào, vì đây là vấn đề về DỮ LIỆU/DATASET,
    # không phải đặc thù riêng của model multi-class.
    age_warning   = get_age_confidence_warning(data_dict.get("age"))
    physio_warnings = check_physiological_warnings(data_dict)

    log_api_request(
        _api_logger, "/predict",
        prediction=prediction, probability=round(prob, 4),
        risk_level=risk_level, age=data_dict.get("age"),
        n_physio_warnings=len(physio_warnings),
        age_warning=age_warning is not None,
    )

    return {
        "prediction" : prediction,
        "probability": round(prob, 4),
        "risk_level" : risk_level,
        "message"    : message,
        "explanation": shap_result["explanation"],
        "top_factors": shap_result["top_factors"],
        "age_confidence_warning": age_warning,
        "physiological_warnings": physio_warnings,
    }

@app.post("/predict/trend")
def predict_trend(data: TrendData):
    """
    [D.16] Phân tích xu hướng dùng TOÀN BỘ lịch sử khám, không chỉ 2
    lần gần nhất. Dùng hồi quy tuyến tính trên toàn chuỗi xác suất để
    phát hiện xu hướng tăng/giảm chậm nhưng đều qua nhiều lần khám.
    """
    ensure_model_ready()
    if len(data.visits) == 0:
        return {"error": "Cần ít nhất 1 lần khám"}

    probs_raw = []
    for visit in data.visits:
        scaled = preprocess(visit.dict())
        prob   = float(model.predict_proba(scaled)[0][1])
        probs_raw.append(prob)

    latest_prob   = probs_raw[-1]
    latest_scaled = preprocess(data.visits[-1].dict())
    prediction    = int(model.predict(latest_scaled)[0])

    trend, trend_msg, trend_detail = analyze_trend_full_history(probs_raw, model_type="binary")

    if latest_prob >= 0.7:
        risk_level = "HIGH"
        message    = "Nguy cơ cao — cần khám chuyên khoa tim mạch ngay"
    elif latest_prob >= 0.4:
        risk_level = "MEDIUM"
        message    = "Nguy cơ trung bình — nên theo dõi và kiểm tra định kỳ"
    else:
        risk_level = "LOW"
        message    = "Nguy cơ thấp — duy trì lối sống lành mạnh"

    shap_result = get_shap_explanation(latest_scaled, data.visits[-1].dict())

    # [D.15][D.17] Cảnh báo dựa trên LẦN KHÁM MỚI NHẤT — các lần khám cũ
    # trong lịch sử đã được nhập/lưu trước đó, không cần cảnh báo lại
    # mỗi lần gọi trend (tránh lặp lại cảnh báo cũ một cách vô ích).
    latest_data = data.visits[-1].dict()
    age_warning     = get_age_confidence_warning(latest_data.get("age"))
    physio_warnings = check_physiological_warnings(latest_data)

    log_api_request(
        _api_logger, "/predict/trend",
        prediction=prediction, probability=round(latest_prob, 4),
        risk_level=risk_level, trend=trend, n_visits=len(data.visits),
        age=latest_data.get("age"),
        n_physio_warnings=len(physio_warnings),
        age_warning=age_warning is not None,
    )

    return {
        "prediction"    : prediction,
        "probability"   : round(latest_prob, 4),
        "risk_level"    : risk_level,
        "message"       : message,
        "trend"         : trend,
        "trend_message" : trend_msg,
        "trend_detail"  : trend_detail,
        "history"       : [round(p, 4) for p in probs_raw],
        "explanation"   : shap_result["explanation"],
        "top_factors"   : shap_result["top_factors"],
        "age_confidence_warning": age_warning,
        "physiological_warnings": physio_warnings,
    }


@app.post("/predict/v2/trend")
def predict_v2_trend(data: TrendData):
    """
    [D.14 + D.16] Trend cho model v2 (multi-class): phân tích xu hướng
    MỨC ĐỘ NẶNG qua toàn bộ lịch sử khám (không chỉ 2 lần gần nhất).
    """
    if model_v2 is None:
        raise HTTPException(status_code=503,
            detail="Model v2 chưa load. Chạy tune_model_v2.py trước.")
    if len(data.visits) == 0:
        return {"error": "Cần ít nhất 1 lần khám"}

    # Dự đoán lớp và xác suất từng lần khám
    classes_raw = []
    prob_severe_raw = []  # dùng P(SEVERE) làm chỉ số liên tục để phân tích trend
    for visit in data.visits:
        scaled = preprocess_v2(visit.dict())
        cls = int(model_v2.predict(scaled)[0])
        probs = model_v2.predict_proba(scaled)[0].tolist()
        classes_raw.append(cls)
        prob_severe_raw.append(probs[3])  # P(SEVERE) = xác suất lớp 3

    latest_class  = classes_raw[-1]
    latest_scaled = preprocess_v2(data.visits[-1].dict())
    latest_probs  = model_v2.predict_proba(latest_scaled)[0].tolist()

    severity_code = class_mapping_v2.get(latest_class, "UNKNOWN")
    risk_tier, message = SEVERITY_MESSAGES.get(severity_code, ("UNKNOWN", ""))
    confidence = round(latest_probs[latest_class], 4)

    trend, trend_msg, trend_detail = analyze_trend_full_history(
        prob_severe_raw, model_type="multiclass")

    # [D.15] Cảnh báo tuổi trẻ cho lần khám mới nhất
    age_warning = get_age_confidence_warning(data.visits[-1].age)

    # [D.17] Validate sinh lý lần khám mới nhất
    physio_warnings = check_physiological_warnings(data.visits[-1].dict())

    log_api_request(
        _api_logger, "/predict/v2/trend",
        predicted_class=latest_class, severity=severity_code,
        confidence=confidence, trend=trend, n_visits=len(data.visits),
        age=data.visits[-1].age,
        n_physio_warnings=len(physio_warnings),
        age_warning=age_warning is not None,
    )

    return {
        "predicted_class"  : latest_class,
        "severity"         : severity_code,
        "risk_tier"        : risk_tier,
        "confidence"       : confidence,
        "probabilities"    : {
            "NO_DISEASE": round(latest_probs[0], 4),
            "MILD"      : round(latest_probs[1], 4),
            "MODERATE"  : round(latest_probs[2], 4),
            "SEVERE"    : round(latest_probs[3], 4),
        },
        "message"          : message,
        "trend"            : trend,
        "trend_message"    : trend_msg,
        "trend_detail"     : trend_detail,
        "class_history"    : classes_raw,
        "prob_severe_history": [round(p, 4) for p in prob_severe_raw],
        "age_confidence_warning": age_warning,
        "physiological_warnings": physio_warnings,
        "model_version"    : "v2",
    }



@app.get("/health")
def health():
    return {
        "status"   : "ok",
        "model_v1" : "RandomForest Binary (AUC)",
        # [FIX] Đọc động từ metrics_v2["algorithm"] thay vì hard-code
        # "RandomForest" — trước đây bị SAI sau khi đổi sang XGBoost vì
        # dòng string này không được cập nhật theo, gây hiển thị nhầm
        # thuật toán thật đang chạy trong production.
        "model_v2" : (f"{metrics_v2.get('algorithm', 'Unknown')} Multi-class (BalAcc)"
                      if model_v2 else "not loaded"),
        "metrics_v1": metrics,
        "metrics_v2": metrics_v2,
    }

@app.get("/model/info")
def model_info():
    """Thông tin chi tiết về model v1 và v2 — ĐỌC ĐỘNG từ artifact, không hard-code"""
    ensure_model_ready()

    # [FIX] Python .get(key, "N/A") CHỈ trả về "N/A" khi KEY THIẾU HẲN —
    # nếu key tồn tại nhưng giá trị là None tường minh (vd. metrics["auc"]
    # = None), .get() vẫn trả về None chứ KHÔNG rơi vào default "N/A"
    # (gotcha kinh điển của Python). Hệ quả: Thymeleaf nhận null thay vì
    # chuỗi "N/A", điều kiện "!= 'N/A'" bên template vẫn đúng vì null khác
    # chuỗi "N/A", rồi code cố format/nhân null → crash giữa lúc render
    # (đã xảy ra thật với field auc/balanced_accuracy_cv_mean...). Hàm
    # dưới đây coalesce CẢ HAI trường hợp (thiếu key VÀ giá trị None) về
    # "N/A", để phía Thymeleaf luôn nhận đúng 1 trong 2: số thật hoặc
    # chuỗi "N/A", không bao giờ là null.
    def safe(d: dict, key: str):
        val = d.get(key)
        return val if val is not None else "N/A"

    v2_algorithm = safe(metrics_v2, "algorithm")
    if v2_algorithm == "N/A":
        v2_algorithm = "Random Forest Multi-class 4 lớp (D.14)"

    return {
        "v1": {
            "algorithm"  : "Random Forest Binary (Tuned)",
            "dataset"    : "UCI Heart Disease 920 patients",
            "auc"        : safe(metrics, "auc"),
            "accuracy"   : safe(metrics, "accuracy"),
            "f1"         : safe(metrics, "f1"),
            "best_params": artifacts.get("best_params", {}),
            "features"   : feature_names,
            "shap_ready" : get_explainer() is not None,
            "shap_error" : _shap_error,
            "endpoint"   : "/predict",
        },
        "v2": {
            # [FIX] Tên thuật toán lấy từ chính artifact đã lưu
            # (metrics["algorithm"]), để khi đổi model (RandomForest ->
            # XGBoost hay ngược lại) chỉ cần thay file .pkl, KHÔNG phải
            # sửa code ở đây — tránh lặp lại lỗi hiển thị sai thuật toán
            # đã xảy ra trước đó.
            "algorithm"       : f"{v2_algorithm} (multi-class 4 lớp)",
            "dataset"         : "UCI Heart Disease 920 patients (oversample)",
            "imbalance_strategy": safe(metrics_v2, "imbalance_strategy"),
            # [FIX] Ưu tiên hiển thị số liệu CV (mean ± std) — đáng tin cậy
            # hơn nhiều so với "balanced_accuracy" từ 1 lần chia train/test
            # đơn lẻ, vốn dễ dao động 5-10 điểm % với dataset chỉ 920 mẫu.
            # Field "balanced_accuracy" cũ vẫn giữ để tương thích ngược.
            "balanced_accuracy"        : safe(metrics_v2, "balanced_accuracy"),
            "balanced_accuracy_cv_mean": safe(metrics_v2, "balanced_accuracy_cv_mean"),
            "balanced_accuracy_cv_std" : safe(metrics_v2, "balanced_accuracy_cv_std"),
            "macro_f1"        : safe(metrics_v2, "macro_f1"),
            "weighted_f1"     : safe(metrics_v2, "weighted_f1"),
            "n_classes"       : 4,
            "class_names"     : class_names_v2,
            "class_mapping"   : class_mapping_v2,
            "best_params"     : artifacts_v2.get("best_params", {}),
            "features"        : feature_names_v2,
            "endpoint"        : "/predict/v2",
            "loaded"          : model_v2 is not None,
        }
    }

# ═══════════════════════════════════════════════════════════════
# [D.17] VALIDATE KHOẢNG GIÁ TRỊ SINH LÝ
# Kiểm tra trước khi đưa vào model — nếu giá trị ngoài khoảng hợp lý,
# báo cảnh báo nhưng vẫn cho chạy (bác sĩ có thể có ca đặc biệt),
# KHÔNG reject như validation categorical (vì đây là dữ liệu liên tục,
# không có "sai" tuyệt đối, chỉ có "bất thường cần xác nhận lại").
# ═══════════════════════════════════════════════════════════════
PHYSIOLOGICAL_RANGES = {
    "age"     : (10, 100,  "Tuổi"),
    "trestbps": (60, 250,  "Huyết áp lúc nghỉ (mmHg)"),
    "chol"    : (50, 600,  "Cholesterol (mg/dL)"),
    "thalch"  : (60, 220,  "Nhịp tim tối đa (bpm)"),
    "oldpeak" : (-2, 10,   "ST Depression (mm)"),
    "ca"      : (0,  3,    "Số mạch vành tắc"),
}

def check_physiological_warnings(data: dict) -> list:
    """
    [D.17] Trả về danh sách cảnh báo (không lỗi) khi giá trị ngoài khoảng
    sinh lý hợp lý. Bác sĩ vẫn nhận được kết quả AI nhưng biết rằng một
    số chỉ số cần xác nhận lại trước khi tin tưởng hoàn toàn vào kết quả.
    """
    warnings_list = []
    for field, (lo, hi, label) in PHYSIOLOGICAL_RANGES.items():
        val = data.get(field)
        if val is None:
            continue
        try:
            v = float(val)
            if v < lo or v > hi:
                warnings_list.append({
                    "field"  : field,
                    "label"  : label,
                    "value"  : v,
                    "range"  : f"{lo}–{hi}",
                    "message": f"{label} = {v} nằm ngoài khoảng sinh lý hợp lý ({lo}–{hi}). "
                               f"Vui lòng xác nhận lại số liệu trước khi tin tưởng kết quả AI."
                })
        except (TypeError, ValueError):
            pass
    return warnings_list


# ═══════════════════════════════════════════════════════════════
# [D.15] CẢNH BÁO ĐỘ TIN CẬY THẤP KHI BỆNH NHÂN DƯỚI 35 TUỔI
# Dataset UCI chỉ có 21/920 mẫu (2.3%) dưới 35 tuổi — model hầu như
# không học được pattern có ý nghĩa cho nhóm này. Thay vì âm thầm trả
# kết quả, phải thông báo rõ để bác sĩ không quá tin tưởng.
# ═══════════════════════════════════════════════════════════════
YOUNG_AGE_THRESHOLD = 35
YOUNG_AGE_TRAINING_COUNT = 21  # số mẫu thực tế trong UCI dưới 35 tuổi

def get_age_confidence_warning(age: float) -> dict | None:
    """
    [D.15] Trả về cảnh báo nếu tuổi dưới ngưỡng, None nếu tuổi OK.
    """
    if age is not None and float(age) < YOUNG_AGE_THRESHOLD:
        return {
            "type"   : "LOW_CONFIDENCE_YOUNG_PATIENT",
            "message": (
                f"⚠️ Độ tin cậy thấp — Bệnh nhân {age:.0f} tuổi (dưới {YOUNG_AGE_THRESHOLD} tuổi). "
                f"Dataset huấn luyện chỉ có {YOUNG_AGE_TRAINING_COUNT}/920 mẫu ({YOUNG_AGE_TRAINING_COUNT/920*100:.1f}%) "
                f"dưới {YOUNG_AGE_THRESHOLD} tuổi, model chưa học đủ pattern cho nhóm tuổi này. "
                f"Kết quả AI cần được xem là THAM KHẢO, không thay thế đánh giá lâm sàng độc lập của bác sĩ."
            ),
            "age"    : age,
            "threshold": YOUNG_AGE_THRESHOLD,
            "training_samples_below_threshold": YOUNG_AGE_TRAINING_COUNT,
        }
    return None


# ═══════════════════════════════════════════════════════════════
# [D.14] PREPROCESS CHO MODEL V2
# Dùng encoder/scaler/imputer của v2 (train trên target 0-3 mức độ nặng)
# ═══════════════════════════════════════════════════════════════
def preprocess_v2(data: dict) -> np.ndarray:
    """Tiền xử lý dữ liệu cho model v2 — tái dùng logic validate từ preprocess()"""
    sample = pd.DataFrame([data])

    for col, le in label_encoders_v2.items():
        if col not in sample.columns:
            continue
        raw_value = sample[col].iloc[0]
        if raw_value is None or (isinstance(raw_value, float) and np.isnan(raw_value)):
            known_has_nan = any(isinstance(c, float) and np.isnan(c) for c in le.classes_)
            if known_has_nan:
                continue
            else:
                raise InvalidCategoricalValueError(
                    field=col, received=None,
                    allowed=[c for c in le.classes_ if not (isinstance(c, float) and np.isnan(c))]
                )
        sample[col] = sample[col].astype(str)
        known = [str(c) for c in le.classes_ if not (isinstance(c, float) and np.isnan(c))]
        value_str = sample[col].iloc[0]
        if value_str not in known:
            raise InvalidCategoricalValueError(field=col, received=value_str, allowed=known)
        sample[col] = le.transform(sample[col])

    sample = sample[feature_names_v2]
    return scaler_v2.transform(imputer_v2.transform(sample))


# ─── Mapping mức độ nặng → thông điệp lâm sàng tiếng Việt ─────
SEVERITY_MESSAGES = {
    "NO_DISEASE": ("NONE",   "Không phát hiện dấu hiệu bệnh tim mạch — duy trì lối sống lành mạnh."),
    "MILD"      : ("LOW",    "Mức độ nhẹ (num=1) — nên theo dõi định kỳ và điều chỉnh lối sống."),
    "MODERATE"  : ("MEDIUM", "Mức độ trung bình (num=2) — cần đánh giá chuyên sâu và can thiệp sớm."),
    "SEVERE"    : ("HIGH",   "Mức độ nặng (num=3-4) — cần khám chuyên khoa tim mạch ngay để điều trị."),
}


# ─── ENDPOINT /predict/v2 ──────────────────────────────────────
@app.post("/predict/v2")
def predict_v2(data: PatientData):
    """
    [D.14] Dự đoán MỨC ĐỘ NẶNG của bệnh tim mạch (4 lớp: không bệnh /
    nhẹ / trung bình / nặng) thay vì chỉ có/không như /predict cũ.

    Cũng tích hợp:
    - [D.15] Cảnh báo độ tin cậy thấp khi bệnh nhân dưới 35 tuổi
    - [D.17] Validate khoảng giá trị sinh lý
    """
    if model_v2 is None:
        raise HTTPException(
            status_code=503,
            detail="Model v2 (multi-class) chưa load được. "
                   "Chạy tune_model_v2.py để tạo heart_model_tuned_v2.pkl trước."
        )

    data_dict = data.dict()

    # [D.17] Validate sinh lý — cảnh báo nếu có giá trị bất thường
    physio_warnings = check_physiological_warnings(data_dict)

    # [D.15] Cảnh báo tuổi trẻ
    age_warning = get_age_confidence_warning(data.age)

    # Preprocess + predict
    sample_scaled = preprocess_v2(data_dict)
    predicted_class   = int(model_v2.predict(sample_scaled)[0])
    probabilities     = model_v2.predict_proba(sample_scaled)[0].tolist()  # [p0, p1, p2, p3]

    severity_code = class_mapping_v2.get(predicted_class, "UNKNOWN")
    risk_tier, message = SEVERITY_MESSAGES.get(severity_code, ("UNKNOWN", "Không xác định"))

    # Xác suất lớp dự đoán (độ tin cậy của kết quả)
    confidence = round(probabilities[predicted_class], 4)

    log_api_request(
        _api_logger, "/predict/v2",
        predicted_class=predicted_class, severity=severity_code,
        confidence=confidence, age=data.age,
        n_physio_warnings=len(physio_warnings),
        age_warning=age_warning is not None,
    )

    return {
        # Kết quả chính
        "predicted_class"  : predicted_class,       # 0, 1, 2, hoặc 3
        "severity"         : severity_code,          # NO_DISEASE / MILD / MODERATE / SEVERE
        "risk_tier"        : risk_tier,              # NONE / LOW / MEDIUM / HIGH (để Spring Boot dùng)
        "confidence"       : confidence,             # xác suất lớp được dự đoán (0.0–1.0)
        "probabilities"    : {                       # xác suất từng lớp rõ ràng
            "NO_DISEASE": round(probabilities[0], 4),
            "MILD"      : round(probabilities[1], 4),
            "MODERATE"  : round(probabilities[2], 4),
            "SEVERE"    : round(probabilities[3], 4),
        },
        "message"          : message,

        # [D.15] Cảnh báo tuổi trẻ (null nếu không áp dụng)
        "age_confidence_warning": age_warning,

        # [D.17] Cảnh báo sinh lý (danh sách rỗng nếu không có vấn đề)
        "physiological_warnings": physio_warnings,

        # Metadata giúp Spring Boot xử lý đúng
        "model_version"    : "v2",
        "n_classes"        : 4,
        "class_names"      : class_names_v2,
    }


# ═══════════════════════════════════════════════════════════════════════
# [D.18] ENDPOINT CHO APP BỆNH NHÂN — /predict/patient
# ═══════════════════════════════════════════════════════════════════════
# Khác với /predict và /predict/v2 (dành cho bác sĩ trên web), endpoint
# này được app di động/web bệnh nhân gọi. Hai khác biệt chính:
#
#   1. INPUT: bệnh nhân chỉ tự đo được một phần chỉ số (tuổi, giới tính,
#      huyết áp, nhịp tim, có đau ngực không, có tiểu đường đã biết
#      không...). Các chỉ số cần xét nghiệm/ECG chuyên sâu (oldpeak,
#      slope, ca, thal, restecg) thường KHÔNG có — để None, SimpleImputer
#      đã train sẵn trong artifacts sẽ tự điền giá trị trung vị thay vì
#      bác sĩ phải nhập tay.
#
#   2. OUTPUT: bác sĩ cần SHAP + xác suất chi tiết; bệnh nhân cần lời
#      khuyên HÀNH VI dễ hiểu (ăn uống / vận động / nghỉ ngơi) và mốc rõ
#      ràng "khi nào cần đi khám ngay" — không trả về xác suất kỹ thuật
#      hay thuật ngữ y khoa khó hiểu để tránh gây hoang mang không cần
#      thiết hoặc bị hiểu sai là chẩn đoán chính thức.
#
# QUAN TRỌNG: đây KHÔNG phải chẩn đoán y khoa, chỉ là khuyến nghị tham
# khảo. Mọi response đều phải có "disclaimer" để Spring Boot/app hiển
# thị rõ cho bệnh nhân, tránh rủi ro pháp lý + an toàn người dùng.
# ═══════════════════════════════════════════════════════════════════════

class PatientSelfReportedData(BaseModel):
    """
    Chỉ gồm các chỉ số bệnh nhân CÓ THỂ tự đo/tự biết tại nhà.
    So với PatientData (dùng cho bác sĩ), KHÔNG có: restecg, oldpeak,
    slope, ca, thal — vì cần ECG/xét nghiệm chuyên sâu mới có được.

    [D.20] Khớp đúng với app Flutter (CardioApp) + Spring Boot trung
    gian: chest_pain_type nhận SỐ NGUYÊN 0-3 (đúng như UI Flutter dùng
    dropdown ánh xạ về số), KHÔNG nhận chuỗi tiếng Anh trực tiếp — tránh
    Spring Boot phải tự dịch sang thuật ngữ UCI, dễ sai lệch ý nghĩa.
    """
    age              : float
    sex              : str                     # "Male" / "Female"
    chest_pain_type  : Optional[int] = None     # 0-3, theo đúng dropdown Flutter (xem CHEST_PAIN_TYPE_MAP)
    trestbps         : Optional[float] = None   # huyết áp đo bằng máy đo tay tại nhà
    chol             : Optional[float] = None   # cholesterol, nếu có kết quả xét nghiệm gần nhất
    fbs              : Optional[str]   = None   # đường huyết lúc đói >120 — nếu bệnh nhân tự biết
    thalch           : Optional[float] = None   # nhịp tim tối đa, đo bằng đồng hồ thông minh/máy đo
    exang            : Optional[str]   = None   # có đau ngực khi gắng sức (đi bộ nhanh, leo thang...)


# [D.20] Map đúng theo thứ tự dropdown trong monitoring_screen.dart (Flutter):
#   0: 'Không đau'          -> asymptomatic     (không triệu chứng đau ngực)
#   1: 'Đau nhẹ lâm râm'    -> non-anginal       (đau không điển hình do tim)
#   2: 'Đau thắt ngực'      -> atypical angina   (đau thắt ngực không điển hình)
#   3: 'Đau nhói cấp tính'  -> typical angina    (đau thắt ngực điển hình — nặng nhất về mặt lâm sàng)
#
# LƯU Ý QUAN TRỌNG: đây KHÔNG phải một thang đo mức độ tuyến tính theo
# y khoa thực sự (4 loại đau ngực UCI là 4 NHÓM lâm sàng khác nhau, không
# phải nhẹ->nặng), nhưng là cách diễn giải hợp lý nhất để map từ 1 thang
# đo đơn giản (cho người dùng phổ thông tự chọn) sang categorical feature
# mà model đã được train trên. Nếu nhóm cập nhật UI Flutter thành mô tả
# triệu chứng cụ thể hơn (vd. có/không đau khi gắng sức), nên map lại.
CHEST_PAIN_TYPE_MAP = {
    0: "asymptomatic",
    1: "non-anginal",
    2: "atypical angina",
    3: "typical angina",
}


# [D.18] Giá trị mặc định AN TOÀN cho field categorical bệnh nhân không
# cung cấp. KHÔNG dựa vào "encoder có từng thấy NaN lúc train hay không"
# (preprocess_v2 cũ) vì điều đó phụ thuộc ngẫu nhiên vào dataset gốc —
# nếu cột categorical đó KHÔNG có giá trị thiếu trong dữ liệu train,
# request của bệnh nhân thiếu field sẽ bị reject 422 dù field đó hoàn
# toàn hợp lý để bỏ trống trong ngữ cảnh tự nhập tại nhà.
#
# Quy tắc chọn default: dùng giá trị "trung tính/không triệu chứng" —
# diễn giải im lặng là "không có thông tin" theo hướng ÍT RỦI RO NHẤT
# (không vô tình đẩy bệnh nhân vào nhóm nguy cơ cao chỉ vì thiếu dữ
# liệu). Áp dụng cho TẤT CẢ cột categorical bệnh nhân không tự đo được
# (restecg, slope, thal) — không chỉ riêng fbs/exang — vì không thể
# đảm bảo trước cột nào trong dataset train có NaN hay không.
#
# "cp" KHÔNG còn nằm trong default map này — vì giờ được derive từ
# chest_pain_type (int 0-3) qua CHEST_PAIN_TYPE_MAP ở build_full_input.
# Nếu chest_pain_type cũng thiếu (None), fallback "asymptomatic" được
# áp dụng riêng trong hàm build_full_input_from_patient_data.
#
# Cột số (oldpeak, ca) vẫn để None cho SimpleImputer (median) xử lý —
# imputer luôn hoạt động độc lập với dataset, an toàn hơn categorical.
PATIENT_CATEGORICAL_DEFAULTS = {
    # [FIX] fbs/exang trong dataset gốc là SỐ (0.0/1.0), KHÔNG phải chuỗi
    # "TRUE"/"FALSE" — phát hiện qua kiểm thử với dataset thật, trước đó
    # chỉ có dữ liệu giả lập để test nên không phát hiện sai lệch này.
    "fbs"    : "0.0",               # không biết tiền sử đường huyết cao -> giả định bình thường
    "exang"  : "0.0",               # không biết đau ngực khi gắng sức -> giả định không có
    "restecg": "normal",            # không có kết quả ECG -> giả định bình thường
    "slope"  : "upsloping",         # không có nghiệm pháp gắng sức -> giả định dạng ít nguy cơ nhất
    "thal"   : "normal",            # không có xét nghiệm Thalassemia -> giả định bình thường
}

PATIENT_NUMERIC_IMPUTED_FIELDS = ("oldpeak", "ca")  # để SimpleImputer xử lý, không set default cứng


def build_full_input_from_patient_data(p: dict) -> dict:
    """
    Bổ sung các field model v2 cần nhưng bệnh nhân không cung cấp.
      - chest_pain_type (int 0-3, từ Flutter) -> "cp" (string UCI) qua
        CHEST_PAIN_TYPE_MAP. Nếu thiếu, fallback "asymptomatic" (ít rủi
        ro nhất khi không có thông tin).
      - oldpeak/ca (số): set None, để SimpleImputer (median) xử lý —
        an toàn vì không phụ thuộc dataset có NaN hay không.
      - fbs/exang/restecg/slope/thal (phân loại): dùng default an
        toàn ở PATIENT_CATEGORICAL_DEFAULTS, vì preprocess_v2() chỉ
        chấp nhận None khi encoder train từng thấy NaN — điều này
        không đảm bảo đúng với mọi dataset, nên không thể tin tưởng.
    """
    full = dict(p)

    chest_pain_type = full.pop("chest_pain_type", None)
    full["cp"] = CHEST_PAIN_TYPE_MAP.get(chest_pain_type, "asymptomatic")

    for field in PATIENT_NUMERIC_IMPUTED_FIELDS:
        full.setdefault(field, None)
    for field, default_value in PATIENT_CATEGORICAL_DEFAULTS.items():
        if full.get(field) is None:
            full[field] = default_value
    return full


# ─── Lời khuyên hành vi theo mức độ nặng (severity) ──────────────────
LIFESTYLE_ADVICE = {
    "NO_DISEASE": {
        "summary": "Chưa phát hiện dấu hiệu bệnh tim mạch đáng lo ngại.",
        "diet": "Duy trì chế độ ăn cân bằng, ít muối, nhiều rau xanh và chất xơ. Hạn chế đồ ăn chiên rán, nhiều dầu mỡ.",
        "exercise": "Duy trì vận động đều đặn 150 phút/tuần (đi bộ nhanh, đạp xe, bơi) theo khuyến nghị AHA.",
        "rest": "Ngủ đủ 7–8 giờ/đêm, tránh thức khuya kéo dài.",
        "follow_up": "Khám sức khỏe định kỳ 1 lần/năm để theo dõi.",
    },
    "MILD": {
        "summary": "Có dấu hiệu nhẹ — nên chủ động điều chỉnh lối sống và theo dõi sát hơn.",
        "diet": "Giảm muối (<5g/ngày), giảm chất béo bão hòa, tăng cá và rau củ. Hạn chế rượu bia, đồ uống có caffeine.",
        "exercise": "Vận động nhẹ-trung bình 30 phút/ngày, 5 ngày/tuần. Tránh gắng sức đột ngột, nên hỏi ý kiến bác sĩ trước khi tập cường độ cao.",
        "rest": "Ưu tiên ngủ đủ giấc, giảm stress (thiền, hít thở sâu). Tránh làm việc quá sức liên tục.",
        "follow_up": "Nên đặt lịch khám với bác sĩ tim mạch trong vài tuần tới để đánh giá kỹ hơn, không cần cấp cứu.",
    },
    "MODERATE": {
        "summary": "Có dấu hiệu ở mức trung bình — cần được bác sĩ đánh giá chuyên sâu sớm.",
        "diet": "Tuân thủ chế độ ăn cho người có bệnh tim mạch (kiểu DASH/Địa Trung Hải): hạn chế muối nghiêm ngặt, tránh mỡ động vật, tránh đồ ăn nhanh.",
        "exercise": "CHỈ vận động nhẹ (đi bộ chậm) và theo hướng dẫn của bác sĩ. Tránh tự tập luyện cường độ cao khi chưa được thăm khám.",
        "rest": "Nghỉ ngơi đầy đủ, tránh căng thẳng và gắng sức. Theo dõi sát các triệu chứng đau ngực, khó thở.",
        "follow_up": "Nên đặt lịch khám chuyên khoa tim mạch trong vòng vài ngày tới, không nên trì hoãn.",
    },
    "SEVERE": {
        "summary": "Có dấu hiệu ở mức nặng — cần được khám và can thiệp y tế kịp thời.",
        "diet": "Tuân thủ tuyệt đối chế độ ăn theo chỉ định bác sĩ, không tự ý điều chỉnh.",
        "exercise": "KHÔNG tự vận động gắng sức. Chỉ hoạt động nhẹ theo hướng dẫn y tế cụ thể.",
        "rest": "Nghỉ ngơi tuyệt đối, tránh mọi căng thẳng và gắng sức thể lực.",
        "follow_up": "⚠️ CẦN ĐẾN CƠ SỞ Y TẾ HOẶC LIÊN HỆ BÁC SĨ CHUYÊN KHOA TIM MẠCH SỚM NHẤT CÓ THỂ. Nếu xuất hiện đau ngực dữ dội, khó thở, choáng váng — gọi cấp cứu (115) ngay.",
    },
}

# Ngưỡng để cảnh báo cấp cứu ngay cả khi chưa ra kết quả model — dựa
# trên triệu chứng tự báo cáo (exang = đau ngực khi gắng sức + tuổi cao
# + huyết áp rất cao là tổ hợp nguy hiểm cần ưu tiên cảnh báo sớm)
EMERGENCY_TRESTBPS_THRESHOLD = 180  # mmHg, ngưỡng tăng huyết áp cấp cứu theo AHA


def get_emergency_flag(data: dict) -> Optional[str]:
    """
    [D.18] Cảnh báo khẩn cấp dựa trên TRIỆU CHỨNG tự báo cáo, độc lập với
    kết quả model — vì model có thể chưa kịp phản ánh tình huống cấp tính,
    và bệnh nhân cần được nhắc đi cấp cứu ngay bất kể severity model trả về.
    """
    trestbps = data.get("trestbps")
    if trestbps is not None:
        try:
            if float(trestbps) >= EMERGENCY_TRESTBPS_THRESHOLD:
                return (
                    f"⚠️ Huyết áp {trestbps} mmHg ở mức RẤT CAO (≥{EMERGENCY_TRESTBPS_THRESHOLD}). "
                    f"Đây có thể là tình trạng cấp cứu tăng huyết áp. Vui lòng liên hệ cơ sở y tế "
                    f"hoặc gọi 115 ngay, đặc biệt nếu kèm đau đầu dữ dội, đau ngực, hoặc khó thở."
                )
        except (TypeError, ValueError):
            pass
    return None


@app.post("/predict/patient")
def predict_patient(data: PatientSelfReportedData):
    """
    [D.18] Endpoint dành cho APP BỆNH NHÂN (khác /predict, /predict/v2
    dành cho web bác sĩ). Nhận chỉ số bệnh nhân tự đo được, trả về lời
    khuyên hành vi (ăn uống / vận động / nghỉ ngơi) thay vì chi tiết kỹ
    thuật như SHAP hay xác suất từng lớp.

    KHÔNG dùng kết quả này thay cho chẩn đoán y khoa chính thức.
    """
    if model_v2 is None:
        raise HTTPException(
            status_code=503,
            detail="Model AI chưa sẵn sàng. Vui lòng thử lại sau hoặc liên hệ bác sĩ trực tiếp."
        )

    data_dict = data.dict()
    full_input = build_full_input_from_patient_data(data_dict)

    # [D.18] Cảnh báo cấp cứu dựa trên triệu chứng — kiểm tra TRƯỚC,
    # độc lập với kết quả model, vì đây là an toàn người dùng ưu tiên cao nhất
    emergency_flag = get_emergency_flag(data_dict)

    # [D.17][D.15] Tái dùng validate đã có cho web bác sĩ
    physio_warnings = check_physiological_warnings(data_dict)
    age_warning = get_age_confidence_warning(data.age)

    sample_scaled   = preprocess_v2(full_input)
    predicted_class = int(model_v2.predict(sample_scaled)[0])
    probabilities   = model_v2.predict_proba(sample_scaled)[0].tolist()
    severity_code   = class_mapping_v2.get(predicted_class, "UNKNOWN")
    risk_tier, _msg = SEVERITY_MESSAGES.get(severity_code, ("UNKNOWN", ""))
    confidence      = round(probabilities[predicted_class], 4)

    advice = LIFESTYLE_ADVICE.get(severity_code, LIFESTYLE_ADVICE["NO_DISEASE"])

    # Vì bệnh nhân tự nhập, thiếu nhiều chỉ số chuyên sâu hơn bác sĩ nhập
    # trên web → độ tin cậy thực tế THẤP HƠN so với khi đủ dữ liệu.
    # Cảnh báo riêng để app hiển thị rõ, tránh bệnh nhân quá tin tưởng.
    missing_numeric = [f for f in PATIENT_NUMERIC_IMPUTED_FIELDS
                        if data_dict.get(f) is None]
    missing_categorical = [f for f in PATIENT_CATEGORICAL_DEFAULTS
                            if data_dict.get(f) is None]
    if data_dict.get("chest_pain_type") is None:
        missing_categorical = ["chest_pain_type"] + missing_categorical
    missing_fields = missing_numeric + missing_categorical

    log_api_request(
        _api_logger, "/predict/patient",
        severity=severity_code, confidence=confidence, age=data.age,
        n_missing_fields=len(missing_fields),
        emergency_flag=emergency_flag is not None,
    )

    return {
        "risk_tier" : risk_tier,           # NONE / LOW / MEDIUM / HIGH
        "severity"  : severity_code,        # nội bộ, app có thể ẩn không hiển thị bệnh nhân
        "summary"   : advice["summary"],

        "recommendations": {
            "diet"     : advice["diet"],
            "exercise" : advice["exercise"],
            "rest"     : advice["rest"],
            "follow_up": advice["follow_up"],
        },

        # Cảnh báo cấp cứu — app PHẢI hiển thị nổi bật nếu khác null
        "emergency_warning": emergency_flag,

        # Cảnh báo độ tin cậy
        "age_confidence_warning": age_warning,
        "physiological_warnings": physio_warnings,
        "limited_data_warning": (
            f"Kết quả dựa trên {13 - len(missing_fields)}/13 chỉ số đầy đủ "
            f"do thiếu xét nghiệm chuyên sâu "
            f"({', '.join(missing_fields)}). Độ chính xác THẤP HƠN so với "
            f"khi được bác sĩ khám đầy đủ."
        ) if missing_fields else None,

        "disclaimer": (
            "Đây KHÔNG phải là chẩn đoán y khoa chính thức. Đây chỉ là khuyến "
            "nghị tham khảo dựa trên AI. Vui lòng đến cơ sở y tế để được bác sĩ "
            "thăm khám và chẩn đoán chính xác, đặc biệt nếu có triệu chứng bất thường."
        ),

        "model_version": "v2-patient",
    }

# ═══════════════════════════════════════════════════════════════════════
# [D.19] CHATBOT — 2 ENDPOINT RIÊNG BIỆT CHO BÁC SĨ (WEB) VÀ BỆNH NHÂN (APP)
# ═══════════════════════════════════════════════════════════════════════
# Theo yêu cầu: tách hoàn toàn 2 endpoint /chat/doctor và /chat/patient,
# KHÔNG dùng chung 1 endpoint với tham số "role" — vì:
#   1. System prompt khác biệt rất lớn (chuyên môn vs thân thiện dễ hiểu)
#   2. Rủi ro an toàn khác nhau (bác sĩ tin tưởng nội bộ vs bệnh nhân có
#      thể hiểu sai thành chẩn đoán) -> dễ audit/log riêng từng endpoint
#   3. Web bác sĩ và App bệnh nhân là 2 client hoàn toàn khác (Spring Boot
#      khác module), tách endpoint giúp versioning độc lập về sau.
#
# Cả 2 cùng dùng llm_client.chat_completion() (module dùng chung, KHÔNG
# chứa logic role-specific) nhưng truyền system_prompt khác nhau.
# ═══════════════════════════════════════════════════════════════════════

class ChatTurn(BaseModel):
    role: str        # "user" hoặc "model" (theo đúng role Gemini API dùng)
    text: str


class DoctorChatRequest(BaseModel):
    """Request cho chatbot bác sĩ — web Doctor Portal."""
    message: str
    history: List[ChatTurn] = []  # lịch sử hội thoại trước đó, rỗng nếu là tin đầu

    # [D.19] Tùy chọn: nếu bác sĩ đang xem 1 kết quả /predict hoặc /predict/v2
    # cụ thể, FE có thể gửi kèm để chatbot giải thích sâu hơn kết quả đó.
    predict_context: Optional[dict] = None

    # [FIX] Tùy chọn: dữ liệu tổng quan phòng khám của bác sĩ (số bệnh nhân
    # phụ trách, số cảnh báo theo mức độ, lịch hẹn hôm nay...) — tính từ
    # Spring Boot (nguồn dữ liệu thật duy nhất), KHÔNG để Gemini tự đoán/
    # bịa số liệu khi bác sĩ hỏi các câu kiểu "tôi đang phụ trách bao nhiêu
    # bệnh nhân". Trước đây thiếu field này khiến chatbot phải trả lời
    # "tôi không có quyền truy cập dữ liệu" cho MỌI câu hỏi dạng tổng quan,
    # dù dữ liệu đó thực ra có sẵn trong hệ thống.
    doctor_context: Optional[dict] = None


class StaffChatRequest(BaseModel):
    """Request cho chatbot điều dưỡng / nhân viên y tế."""
    message: str
    history: List[ChatTurn] = []
    predict_context: Optional[dict] = None
    staff_context: Optional[dict] = None


class PatientChatRequest(BaseModel):
    """Request cho chatbot bệnh nhân — app di động."""
    message: str
    history: List[ChatTurn] = []

    # [D.19] Tùy chọn: kết quả từ /predict/patient gần nhất, nếu app muốn
    # chatbot giải thích thêm lời khuyên đã nhận được.
    predict_context: Optional[dict] = None


class ChatResponse(BaseModel):
    reply: str
    disclaimer: str


# ─── System prompt riêng cho từng đối tượng ────────────────────────────
# [FIX] Build ĐỘNG phần mô tả model thay vì hard-code cứng trong string,
# để mỗi lần đổi model (.pkl mới) prompt tự cập nhật theo, không lặp lại
# lỗi "chatbot nói sai thuật toán" đã từng xảy ra khi đổi RandomForest
# sang XGBoost mà quên sửa system prompt.
def _build_doctor_system_prompt() -> str:
    v2_algo = metrics_v2.get("algorithm", "chưa xác định")
    v2_cv_mean = metrics_v2.get("balanced_accuracy_cv_mean")
    v2_cv_std  = metrics_v2.get("balanced_accuracy_cv_std")
    if v2_cv_mean is not None and v2_cv_std is not None:
        v2_perf_note = f"balanced_accuracy ≈ {v2_cv_mean:.2f} ± {v2_cv_std:.2f} (5-fold CV)"
    else:
        v2_perf_note = f"balanced_accuracy ≈ {metrics_v2.get('balanced_accuracy', 'N/A')}"

    return f"""Bạn là trợ lý AI hỗ trợ BÁC SĨ trong hệ thống CardioCare —
một nền tảng phát hiện sớm bệnh tim mạch, gồm 2 model:
  • Model nhị phân (endpoint /predict): Random Forest, dự đoán có/không
    có bệnh, AUC ≈ {metrics.get('auc', 'N/A')}.
  • Model đa lớp (endpoint /predict/v2): {v2_algo}, dự đoán 4 mức độ nặng
    (Không bệnh/Nhẹ/Trung bình/Nặng), {v2_perf_note}.
Cả 2 đều dùng SHAP để giải thích kết quả và huấn luyện trên UCI Heart
Disease (920 bệnh nhân).

Đối tượng trò chuyện với bạn LÀ BÁC SĨ CHUYÊN MÔN, không phải bệnh nhân.
Vì vậy:
- Có thể dùng thuật ngữ y khoa, tên chỉ số kỹ thuật (SHAP, oldpeak, ST
  depression, balanced accuracy...) mà không cần giải thích lại từ đầu.
- Khi được hỏi về một kết quả dự đoán cụ thể (predict_context nếu có),
  hãy giải thích các yếu tố ảnh hưởng, độ tin cậy, và giới hạn của model
  — đặc biệt lưu ý: model đa lớp có độ dao động balanced_accuracy khá lớn
  giữa các lần đánh giá (do dataset chỉ 920 mẫu, 4 lớp mất cân bằng), nên
  luôn nói rõ đây là khoảng ước lượng (mean ± std), không phải con số cố
  định tuyệt đối.
- Khi được hỏi các câu tổng quan về phòng khám (số bệnh nhân phụ trách,
  số cảnh báo, lịch hẹn hôm nay...) và có [Số liệu tổng quan phòng khám
  hiện tại] trong tin nhắn, hãy TRẢ LỜI TRỰC TIẾP bằng đúng số liệu đó —
  KHÔNG nói "tôi không có quyền truy cập dữ liệu" khi số liệu đã được
  cung cấp sẵn. Nếu không thấy phần này trong tin nhắn (không có dữ liệu
  kèm theo), mới nói rõ là không có dữ liệu để trả lời chính xác.
- Bạn có thể thảo luận về dataset UCI Heart Disease, ý nghĩa lâm sàng
  của các chỉ số, và cách diễn giải kết quả AI — nhưng PHẢI luôn nói rõ
  đây là công cụ HỖ TRỢ quyết định, không thay thế đánh giá lâm sàng.
- KHÔNG tự đưa ra chẩn đoán xác định cho 1 bệnh nhân cụ thể — chỉ giải
  thích những gì model/dữ liệu cho thấy.
- Trả lời ngắn gọn, đúng trọng tâm, theo phong cách chuyên môn — không
  cần giải thích y khoa cơ bản mà bác sĩ đã biết.
- [QUAN TRỌNG] Khi bác sĩ hỏi mơ hồ/rộng như "toàn bộ luôn", "tất cả
  thông tin", "cho tôi biết hết"... KHÔNG cố liệt kê TOÀN BỘ dữ liệu thô
  (mọi SHAP factor, mọi chỉ số) trong 1 câu trả lời dài dằng dặc — điều
  này dễ khiến câu trả lời rối, lặp lại nội dung đã nói trước đó trong
  hội thoại, hoặc bị cụt giữa chừng. Thay vào đó:
    (a) Tóm tắt CÓ CHỌN LỌC những điểm quan trọng nhất (tối đa ~150 từ),
    (b) Hỏi lại bác sĩ muốn đào sâu vào phần nào cụ thể (ví dụ: "Bác sĩ
        muốn xem chi tiết về yếu tố nguy cơ, chỉ số lâm sàng, hay xu
        hướng theo thời gian?"), thay vì đoán và liệt kê tất cả.
- KHÔNG lặp lại nguyên văn thông tin đã cung cấp ở lượt trả lời trước
  trong CÙNG cuộc hội thoại — nếu bác sĩ hỏi thêm, bổ sung thông tin MỚI
  hoặc góc nhìn khác, không nhắc lại y hệt.
- Giới hạn mỗi câu trả lời trong khoảng 100-200 từ trừ khi bác sĩ yêu
  cầu rõ ràng một bản phân tích đầy đủ, chi tiết.
- Trả lời bằng tiếng Việt trừ khi được hỏi bằng ngôn ngữ khác."""


DOCTOR_SYSTEM_PROMPT = _build_doctor_system_prompt()

def _build_staff_system_prompt() -> str:
    v2_algo = metrics_v2.get("algorithm", "chưa xác định")
    v2_cv_mean = metrics_v2.get("balanced_accuracy_cv_mean")
    v2_cv_std  = metrics_v2.get("balanced_accuracy_cv_std")
    if v2_cv_mean is not None and v2_cv_std is not None:
        v2_perf_note = f"balanced_accuracy ≈ {v2_cv_mean:.2f} ± {v2_cv_std:.2f} (5-fold CV)"
    else:
        v2_perf_note = f"balanced_accuracy ≈ {metrics_v2.get('balanced_accuracy', 'N/A')}"

    return f"""Bạn là trợ lý AI hỗ trợ ĐIỀU DƯỠNG / NHÂN VIÊN Y TẾ (Medical Staff) trong hệ thống CardioCare —
một nền tảng phát hiện sớm bệnh tim mạch, gồm 2 model:
  • Model nhị phân (endpoint /predict): Random Forest, dự đoán có/không
    có bệnh, AUC ≈ {metrics.get('auc', 'N/A')}.
  • Model đa lớp (endpoint /predict/v2): {v2_algo}, dự đoán 4 mức độ nặng
    (Không bệnh/Nhẹ/Trung bình/Nặng), {v2_perf_note}.
Cả 2 đều dùng SHAP để giải thích kết quả và huấn luyện trên UCI Heart
Disease (920 bệnh nhân).

Đối tượng trò chuyện với bạn LÀ ĐIỀU DƯỠNG / NHÂN VIÊN Y TẾ (Medical Staff), không phải Bác sĩ và không phải Bệnh nhân.
Vì vậy:
- Tập trung vào hỗ trợ công việc điều dưỡng: giải thích ý nghĩa các chỉ số sinh tồn cơ bản (huyết áp, cholesterol, nhịp tim, đường huyết), ngưỡng bình thường/bất thường, hướng dẫn cách ghi nhận chỉ số sinh tồn và kết quả xét nghiệm chính xác vào hệ thống.
- Khi được hỏi về kết quả dự đoán AI của một ca khám (predict_context nếu có), hãy giúp họ giải thích sơ bộ các chỉ số của bệnh nhân có vượt ngưỡng bình thường không, mức độ cảnh báo nguy cơ (Đỏ - HIGH / Vàng - MEDIUM / Xanh - LOW), và hướng dẫn họ cách theo dõi hoặc bàn giao/báo cáo ca bệnh nguy cơ cao cho Bác sĩ điều trị.
- Nhắc nhở điều dưỡng về vai trò chuyên môn: Điều dưỡng KHÔNG được tự ý chẩn đoán xác định bệnh, kê đơn thuốc hay đưa ra phác đồ điều trị. Mọi chẩn đoán và phác đồ phải do Bác sĩ thực hiện.
- Trả lời ngắn gọn, thân thiện, dễ hiểu, theo phong cách y tế chuẩn mực.
- Trả lời bằng tiếng Việt trừ khi được hỏi bằng ngôn ngữ khác."""

STAFF_SYSTEM_PROMPT = _build_staff_system_prompt()


PATIENT_SYSTEM_PROMPT = """Bạn là trợ lý AI sức khỏe tim mạch trong app CardioCare,
trò chuyện trực tiếp với BỆNH NHÂN (không có chuyên môn y khoa).

QUY TẮC AN TOÀN BẮT BUỘC — không được vi phạm dù người dùng yêu cầu:
1. KHÔNG đưa ra chẩn đoán y khoa. Không bao giờ nói "bạn bị bệnh X" hay
   "bạn không có bệnh gì" một cách chắc chắn — chỉ có bác sĩ mới được
   chẩn đoán chính thức.
2. Nếu người dùng mô tả triệu chứng nguy hiểm (đau ngực dữ dội, khó thở,
   choáng váng, tê yếu tay chân...), LUÔN khuyên họ liên hệ cấp cứu (115)
   hoặc đến cơ sở y tế ngay — đặt điều này lên đầu câu trả lời.
3. Không tự đưa ra liều lượng thuốc hay điều chỉnh thuốc đang dùng —
   luôn khuyên hỏi bác sĩ/dược sĩ.
4. Nếu có predict_context (kết quả từ /predict/patient), có thể giải
   thích lại lời khuyên đó bằng ngôn ngữ đơn giản hơn, nhưng PHẢI giữ
   nguyên tinh thần "đây là tham khảo, không phải chẩn đoán".
5. Luôn dùng ngôn ngữ thân thiện, dễ hiểu, tránh thuật ngữ y khoa phức
   tạp — nếu cần dùng, giải thích ngay bằng từ ngữ đời thường.
6. Khi không chắc hoặc câu hỏi vượt ngoài phạm vi tư vấn lối sống chung,
   khuyên người dùng hỏi trực tiếp bác sĩ thay vì đoán.

Bạn CÓ THỂ trò chuyện về: dinh dưỡng tốt cho tim mạch, lợi ích vận động,
cách giảm stress, ý nghĩa các chỉ số sức khỏe ở mức cơ bản, và giải thích
lại các khuyến nghị app đã đưa ra.

Trả lời bằng tiếng Việt trừ khi được hỏi bằng ngôn ngữ khác. Giữ câu trả
lời ngắn gọn, ấm áp, dễ hiểu."""


CHAT_DISCLAIMER_DOCTOR = (
    "Câu trả lời từ AI chỉ mang tính hỗ trợ tham khảo, không thay thế "
    "đánh giá lâm sàng và quyết định chuyên môn của bác sĩ."
)
CHAT_DISCLAIMER_PATIENT = (
    "Đây KHÔNG phải là chẩn đoán y khoa. Vui lòng đến cơ sở y tế để được "
    "bác sĩ thăm khám, đặc biệt nếu bạn có triệu chứng bất thường."
)


def _history_to_dicts(history: List[ChatTurn]) -> list[dict]:
    return [{"role": h.role, "text": h.text} for h in history]


def _build_user_message_with_context(
    message: str,
    predict_context: Optional[dict],
    doctor_context: Optional[dict] = None,
) -> str:
    """
    Ghép predict_context (kết quả AI 1 lần khám cụ thể) và/hoặc doctor_context
    (số liệu tổng quan phòng khám: số bệnh nhân, cảnh báo, lịch hẹn hôm nay)
    vào đầu tin nhắn, để model có dữ liệu THẬT thay vì phải từ chối trả lời
    hoặc bịa số khi bác sĩ hỏi các câu hỏi tổng quan.
    """
    parts = []
    if doctor_context:
        parts.append(f"[Số liệu tổng quan phòng khám hiện tại]: {doctor_context}")
    if predict_context:
        parts.append(f"[Bối cảnh — kết quả dự đoán AI gần nhất]: {predict_context}")

    if not parts:
        return message

    return "\n\n".join(parts) + f"\n\n[Câu hỏi của người dùng]: {message}"


@app.post("/chat/doctor", response_model=ChatResponse)
def chat_doctor(req: DoctorChatRequest):
    """
    [D.19] Chatbot dành RIÊNG cho bác sĩ trên Web Doctor Portal.
    KHÔNG dùng chung endpoint với /chat/patient.
    """
    if not _CHATBOT_AVAILABLE:
        raise HTTPException(
            status_code=503,
            detail="Chatbot chưa được cấu hình (thiếu llm_client.py hoặc "
                   "google-genai). Liên hệ admin hệ thống."
        )

    user_message = _build_user_message_with_context(
        req.message, req.predict_context, req.doctor_context
    )

    try:
        reply = chat_completion(
            system_prompt=DOCTOR_SYSTEM_PROMPT,
            history=_history_to_dicts(req.history),
            user_message=user_message,
        )
    except ChatError as e:
        log_api_request(_api_logger, "/chat/doctor", status="ERROR", error=str(e))
        raise HTTPException(
            status_code=502,
            detail=f"Chatbot tạm thời không khả dụng: {e}"
        )

    log_api_request(
        _api_logger, "/chat/doctor", status="OK",
        message_len=len(req.message), has_context=req.predict_context is not None,
        has_doctor_context=req.doctor_context is not None,
        history_turns=len(req.history),
    )

    return ChatResponse(reply=reply, disclaimer=CHAT_DISCLAIMER_DOCTOR)


CHAT_DISCLAIMER_STAFF = (
    "Câu trả lời từ AI chỉ mang tính hỗ trợ tham khảo, không thay thế "
    "đánh giá chuyên môn và vai trò chẩn đoán/điều trị của bác sĩ."
)


@app.post("/chat/staff", response_model=ChatResponse)
def chat_staff(req: StaffChatRequest):
    """
    Chatbot dành RIÊNG cho điều dưỡng / nhân viên y tế trên Web Staff Portal.
    """
    if not _CHATBOT_AVAILABLE:
        raise HTTPException(
            status_code=503,
            detail="Chatbot chưa được cấu hình (thiếu llm_client.py hoặc "
                   "google-genai). Liên hệ admin hệ thống."
        )

    user_message = _build_user_message_with_context(
        req.message, req.predict_context, req.staff_context
    )

    try:
        reply = chat_completion(
            system_prompt=STAFF_SYSTEM_PROMPT,
            history=_history_to_dicts(req.history),
            user_message=user_message,
        )
    except ChatError as e:
        log_api_request(_api_logger, "/chat/staff", status="ERROR", error=str(e))
        raise HTTPException(
            status_code=502,
            detail=f"Chatbot tạm thời không khả dụng: {e}"
        )

    log_api_request(
        _api_logger, "/chat/staff", status="OK",
        message_len=len(req.message), has_context=req.predict_context is not None,
        history_turns=len(req.history),
    )

    return ChatResponse(reply=reply, disclaimer=CHAT_DISCLAIMER_STAFF)


@app.post("/chat/patient", response_model=ChatResponse)
def chat_patient(req: PatientChatRequest):
    """
    [D.19] Chatbot dành RIÊNG cho bệnh nhân trên App di động.
    KHÔNG dùng chung endpoint với /chat/doctor — system prompt, giới hạn
    an toàn, và cách diễn đạt hoàn toàn khác (xem PATIENT_SYSTEM_PROMPT).
    """
    if not _CHATBOT_AVAILABLE:
        raise HTTPException(
            status_code=503,
            detail="Trợ lý AI tạm thời không khả dụng. Vui lòng thử lại sau "
                   "hoặc liên hệ bác sĩ trực tiếp."
        )

    user_message = _build_user_message_with_context(req.message, req.predict_context)

    try:
        reply = chat_completion(
            system_prompt=PATIENT_SYSTEM_PROMPT,
            history=_history_to_dicts(req.history),
            user_message=user_message,
            temperature=0.5,
        )
    except ChatError as e:
        log_api_request(_api_logger, "/chat/patient", status="ERROR", error=str(e))
        raise HTTPException(
            status_code=502,
            detail="Trợ lý AI tạm thời không khả dụng. Vui lòng thử lại sau."
        )

    log_api_request(
        _api_logger, "/chat/patient", status="OK",
        message_len=len(req.message), has_context=req.predict_context is not None,
        history_turns=len(req.history),
    )

    return ChatResponse(reply=reply, disclaimer=CHAT_DISCLAIMER_PATIENT)


# ═══════════════════════════════════════════════════════════════════════
# [D.24] TẢI LOG THỦ CÔNG — khắc phục vấn đề Render free tier có filesystem
# tạm thời (ephemeral): mọi file ghi ra trong lúc chạy (bao gồm
# cardiocare_ai_sessions.log) sẽ MẤT mỗi khi service redeploy/restart/ngủ
# rồi thức lại. Vì Render free tier KHÔNG hỗ trợ persistent disk (kể cả
# bản trả phí thấp nhất), cách đơn giản nhất là tự tải file log này về máy
# TRƯỚC MỖI LẦN git push (vì push = redeploy = mất log).
#
# Bảo vệ bằng 1 secret key đơn giản qua query param — vì service này public
# trên internet, không nên để ai cũng tải được log hệ thống. Đặt biến môi
# trường ADMIN_LOG_KEY trên Render (Environment → Add Environment Variable),
# nếu không set thì DÙNG "changeme" làm mặc định (KHÔNG an toàn, chỉ để
# tránh crash khi quên set — nên đổi ngay trong thực tế).
# ═══════════════════════════════════════════════════════════════════════

ADMIN_LOG_KEY = os.environ.get("ADMIN_LOG_KEY", "changeme")


def _check_admin_key(key: str):
    if key != ADMIN_LOG_KEY:
        raise HTTPException(status_code=403, detail="Sai admin key.")


@app.get("/admin/download-log")
def download_log(key: str = Query(..., description="Admin key để xác thực")):
    """
    [D.24] Tải file cardiocare_ai_sessions.log hiện tại (đang tồn tại trên
    filesystem tạm thời của Render lúc này). GỌI ĐỊNH KỲ, đặc biệt TRƯỚC
    MỖI LẦN git push, vì sau khi redeploy file này sẽ bị xóa sạch (ephemeral
    filesystem — xem giải thích ở đầu block D.24).

    Cách dùng: mở trên trình duyệt
        https://cardiocare-ai.onrender.com/admin/download-log?key=<ADMIN_LOG_KEY>
    trình duyệt sẽ tự tải file .log về máy.
    """
    _check_admin_key(key)
    if not SESSION_LOG.exists():
        raise HTTPException(
            status_code=404,
            detail="Chưa có file log nào trên server (có thể service vừa mới khởi động lại)."
        )
    return FileResponse(
        path=str(SESSION_LOG),
        filename="cardiocare_ai_sessions.log",
        media_type="text/plain",
    )


@app.get("/admin/download-summary")
def download_summary(key: str = Query(..., description="Admin key để xác thực")):
    """[D.24] Tương tự download_log nhưng cho file tóm tắt cardiocare_ai_summary.txt."""
    _check_admin_key(key)
    if not SUMMARY_FILE.exists():
        raise HTTPException(
            status_code=404,
            detail="Chưa có file summary nào trên server."
        )
    return FileResponse(
        path=str(SUMMARY_FILE),
        filename="cardiocare_ai_summary.txt",
        media_type="text/plain",
    )


@app.get("/admin/log-preview")
def preview_log(key: str = Query(..., description="Admin key để xác thực"),
                 lines: int = Query(50, description="Số dòng cuối muốn xem")):
    """
    [D.24] Xem nhanh N dòng CUỐI của log ngay trên trình duyệt/Postman,
    không cần tải file — tiện để kiểm tra nhanh log còn hay đã mất, trước
    khi quyết định có cần tải file đầy đủ hay không.
    """
    _check_admin_key(key)
    if not SESSION_LOG.exists():
        return {"exists": False, "lines": []}
    with open(SESSION_LOG, "r", encoding="utf-8") as f:
        all_lines = f.readlines()
    return {
        "exists": True,
        "total_lines_in_file": len(all_lines),
        "last_lines": [l.rstrip("\n") for l in all_lines[-lines:]],
    }