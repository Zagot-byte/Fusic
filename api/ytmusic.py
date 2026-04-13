import ytmusicapi
from ytmusicapi import YTMusic
import asyncio
import logging
import os

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

yt = None

def init_ytmusic():
    global yt
    auth_methods = ["oauth.json", "headers_auth.json"]
    for method in auth_methods:
        if os.path.exists(method):
            try:
                yt = YTMusic(method)
                logger.info(f"Loaded ytmusicapi from {method}")
                return
            except Exception as e:
                logger.error(f"Failed to load {method}: {e}")
    
    logger.info("Initializing ytmusicapi unauthenticated")
    yt = YTMusic()

init_ytmusic()

async def search(q: str, filter: str = 'songs') -> list:
    valid = ['songs', 'videos', 'albums', 'artists', 'playlists']
    f = filter if filter in valid else 'songs'
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, lambda: yt.search(q, filter=f, limit=20))

async def suggestions(q: str) -> list:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, lambda: yt.get_search_suggestions(q))

async def home() -> list:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, lambda: yt.get_home(limit=6))

async def artist(channel_id: str) -> dict:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, lambda: yt.get_artist(channel_id))

async def album(browse_id: str) -> dict:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, lambda: yt.get_album(browse_id))

async def radio(video_id: str) -> dict:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, lambda: yt.get_watch_playlist(videoId=video_id, limit=25))

async def lyrics(browse_id: str) -> dict:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, lambda: yt.get_lyrics(browse_id))

async def charts(country: str = 'ZZ') -> dict:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, lambda: yt.get_charts(country=country))

async def moods() -> dict:
    loop = asyncio.get_event_loop()
    return await loop.run_in_executor(None, lambda: yt.get_mood_categories())
