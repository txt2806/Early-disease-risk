"""
ai_logger.py — Module ghi log tự động cho CardioCare AI
════════════════════════════════════════════════════════
Import vào tune_model.py, tune_model_v2.py, api_v2.py.
Mỗi lần chạy tự động ghi vào:
  • cardiocare_ai_sessions.log  ← log tích lũy KHÔNG BAO GIỜ XOÁ
  • cardiocare_ai_summary.txt   ← bảng tóm tắt dễ đọc để nộp báo cáo
"""

import os
import sys
import time
import logging
from datetime import datetime
from pathlib import Path

# ─── Đường dẫn log cố định cạnh file này ──────────────────────
_LOG_DIR  = Path(__file__).resolve().parent
SESSION_LOG  = _LOG_DIR / "cardiocare_ai_sessions.log"
SUMMARY_FILE = _LOG_DIR / "cardiocare_ai_summary.txt"

# ─── Formatter hiển thị timestamp đầy đủ ─────────────────────
_fmt = logging.Formatter(
    fmt="[%(asctime)s] [%(name)s] %(levelname)s — %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)

def get_logger(name: str) -> logging.Logger:
    """
    Tạo logger ghi đồng thời ra:
      - console (stdout)
      - cardiocare_ai_sessions.log (append, không bao giờ xoá)
    """
    logger = logging.getLogger(name)
    if logger.handlers:          # tránh thêm handler 2 lần khi import nhiều lần
        return logger
    logger.setLevel(logging.DEBUG)

    # Handler 1: Console
    ch = logging.StreamHandler(sys.stdout)
    ch.setLevel(logging.INFO)
    ch.setFormatter(_fmt)
    logger.addHandler(ch)

    # Handler 2: File (append mode — KHÔNG truncate)
    fh = logging.FileHandler(SESSION_LOG, mode="a", encoding="utf-8")
    fh.setLevel(logging.DEBUG)
    fh.setFormatter(_fmt)
    logger.addHandler(fh)

    return logger


class SessionTimer:
    """
    Context manager đo thời gian và ghi session vào log + summary.

    Dùng:
        with SessionTimer("tune_model_v2", logger) as t:
            ... code chạy model ...
            t.record("balanced_accuracy", 0.4876)
            t.record("macro_f1", 0.4777)
    """

    def __init__(self, session_name: str, logger: logging.Logger):
        self.name    = session_name
        self.logger  = logger
        self.metrics = {}
        self._start  = None

    def __enter__(self):
        self._start = time.time()
        self.started_at = datetime.now()
        self.logger.info("=" * 60)
        self.logger.info(f"SESSION BẮT ĐẦU: {self.name}")
        self.logger.info(f"Thời điểm: {self.started_at.strftime('%Y-%m-%d %H:%M:%S')}")
        self.logger.info(f"Python: {sys.version.split()[0]} | PID: {os.getpid()}")
        self.logger.info("=" * 60)
        return self

    def record(self, key: str, value):
        """Ghi một metric vào session (gọi nhiều lần được)."""
        self.metrics[key] = value
        self.logger.info(f"  METRIC | {key}: {value}")

    def __exit__(self, exc_type, exc_val, exc_tb):
        elapsed  = time.time() - self._start
        ended_at = datetime.now()

        if exc_type:
            self.logger.error(f"SESSION LỖI: {exc_type.__name__}: {exc_val}")
            status = "FAILED"
        else:
            status = "SUCCESS"

        self.logger.info("─" * 60)
        self.logger.info(f"SESSION KẾT THÚC: {self.name}")
        self.logger.info(f"Trạng thái   : {status}")
        self.logger.info(f"Thời gian    : {elapsed:.1f}s ({elapsed/60:.1f} phút)")
        self.logger.info(f"Thời điểm    : {ended_at.strftime('%Y-%m-%d %H:%M:%S')}")
        for k, v in self.metrics.items():
            self.logger.info(f"  {k:30s}: {v}")
        self.logger.info("=" * 60)

        # Ghi vào summary file (dạng bảng dễ đọc để nộp báo cáo)
        self._append_summary(elapsed, ended_at, status)

        return False  # không suppress exception

    def _append_summary(self, elapsed, ended_at, status):
        """Ghi 1 dòng tóm tắt vào cardiocare_ai_summary.txt."""
        metrics_str = " | ".join(f"{k}={v}" for k, v in self.metrics.items())
        line = (
            f"{self.started_at.strftime('%Y-%m-%d %H:%M:%S')}  "
            f"{ended_at.strftime('%H:%M:%S')}  "
            f"{elapsed:7.1f}s  "
            f"{status:8s}  "
            f"{self.name:30s}  "
            f"{metrics_str}\n"
        )

        # Tạo header nếu file chưa tồn tại
        if not SUMMARY_FILE.exists():
            header = (
                "CardioCare AI — Session Summary\n"
                "Tích lũy từng lần chạy, không xoá\n"
                f"{'─'*120}\n"
                f"{'START':20s}  {'END':8s}  {'ELAPSED':7s}  {'STATUS':8s}  "
                f"{'SESSION':30s}  METRICS\n"
                f"{'─'*120}\n"
            )
            SUMMARY_FILE.write_text(header, encoding="utf-8")

        with open(SUMMARY_FILE, "a", encoding="utf-8") as f:
            f.write(line)


def log_api_request(logger: logging.Logger, endpoint: str, **fields):
    """
    Ghi 1 dòng log cho mỗi lần API được gọi (vd. /predict, /predict/v2).

    Khác với SessionTimer (dùng cho các phiên train dài, có start/end,
    đo elapsed time), hàm này dùng cho các request tức thì — không cần
    context manager, chỉ cần gọi 1 lần sau khi có kết quả:

        log_api_request(
            _api_logger, "/predict/v2",
            severity=severity_code, confidence=confidence, age=data.age
        )

    Ghi vào CÙNG file cardiocare_ai_sessions.log (tích lũy, không xoá)
    để có đầy đủ lịch sử cả lúc TRAIN model và lúc DÙNG model trên web.
    """
    fields_str = " | ".join(f"{k}={v}" for k, v in fields.items())
    logger.info(f"API_CALL | endpoint={endpoint} | {fields_str}")