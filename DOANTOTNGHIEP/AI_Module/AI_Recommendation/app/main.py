import sys
import time
from contextlib import asynccontextmanager
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

sys.path.insert(0, str(Path(__file__).parent.parent))

from app.api.routes import router
from app.recommender.indexer import get_index
from app.reranker.trainer import get_model


@asynccontextmanager
async def lifespan(app: FastAPI):
    # ── Startup: load models vào RAM một lần duy nhất ─────────────────────────
    print("[startup] Loading FAISS index...")
    t0 = time.perf_counter()
    bundle = get_index()
    print(f"[startup] FAISS ready — {len(bundle)} rooms ({time.perf_counter()-t0:.3f}s)")

    print("[startup] Loading LightGBM model...")
    t0 = time.perf_counter()
    get_model()
    print(f"[startup] LightGBM ready ({time.perf_counter()-t0:.3f}s)")

    yield

    # ── Shutdown ───────────────────────────────────────────────────────────────
    print("[shutdown] Bye!")


app = FastAPI(
    title="AI Room Recommendation API",
    description="Content-Based KNN + LightGBM re-ranker cho hệ thống gợi ý phòng trọ",
    version="1.0.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router, prefix="/api/v1")
