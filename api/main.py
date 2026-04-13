from fastapi import FastAPI, Depends, Query
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from auth import verify_key
from ytmusic import (
    search as yt_search, suggestions as yt_suggestions, home as yt_home,
    artist as yt_artist, album as yt_album, radio as yt_radio,
    lyrics as yt_lyrics, charts as yt_charts, moods as yt_moods
)

app = FastAPI(title="fusic-api")

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

@app.get("/search")
async def search(q: str = Query(...), filter: str = Query('songs'), _: None = Depends(verify_key)):
    try:
        return await yt_search(q, filter)
    except Exception as e:
        return JSONResponse({'error': str(e)}, status_code=502)

@app.get("/suggestions")
async def suggestions(q: str = Query(...), _: None = Depends(verify_key)):
    try:
        return await yt_suggestions(q)
    except Exception as e:
        return JSONResponse({'error': str(e)}, status_code=502)

@app.get("/home")
async def home(_: None = Depends(verify_key)):
    try:
        return await yt_home()
    except Exception as e:
        return JSONResponse({'error': str(e)}, status_code=502)

@app.get("/artist/{channelId}")
async def artist(channelId: str, _: None = Depends(verify_key)):
    try:
        return await yt_artist(channelId)
    except Exception as e:
        return JSONResponse({'error': str(e)}, status_code=502)

@app.get("/album/{browseId}")
async def album(browseId: str, _: None = Depends(verify_key)):
    try:
        return await yt_album(browseId)
    except Exception as e:
        return JSONResponse({'error': str(e)}, status_code=502)

@app.get("/radio/{videoId}")
async def radio(videoId: str, _: None = Depends(verify_key)):
    try:
        return await yt_radio(videoId)
    except Exception as e:
        return JSONResponse({'error': str(e)}, status_code=502)

@app.get("/lyrics/{browseId}")
async def lyrics(browseId: str, _: None = Depends(verify_key)):
    try:
        return await yt_lyrics(browseId)
    except Exception as e:
        return JSONResponse({'error': str(e)}, status_code=502)

@app.get("/charts")
async def charts(country: str = Query('ZZ'), _: None = Depends(verify_key)):
    try:
        return await yt_charts(country)
    except Exception as e:
        return JSONResponse({'error': str(e)}, status_code=502)

@app.get("/moods")
async def moods(_: None = Depends(verify_key)):
    try:
        return await yt_moods()
    except Exception as e:
        return JSONResponse({'error': str(e)}, status_code=502)
