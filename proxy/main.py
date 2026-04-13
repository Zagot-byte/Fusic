from fastapi import FastAPI, Depends
from fastapi.middleware.cors import CORSMiddleware
from auth import verify_key
from audio import resolve_url, stream_audio
from image import proxy_image

app = FastAPI(title="fusic-proxy")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/health")
async def health():
    return {"ok": True}

@app.get("/resolve/{videoId}")
async def resolve(videoId: str, _: None = Depends(verify_key)):
    return await resolve_url(videoId)

@app.get("/stream/{videoId}")
async def stream(videoId: str, range: str | None = __import__('fastapi').Header(None), _: None = Depends(verify_key)):
    return await stream_audio(videoId, range)

@app.get("/image")
async def image(url: str, _: None = Depends(verify_key)):
    return await proxy_image(url)
