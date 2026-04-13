import yt_dlp
import asyncio
import httpx
import logging
import os
import urllib.parse
from fastapi.responses import StreamingResponse

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

YDL_OPTS = {
    'format': '141/bestaudio[ext=m4a]/bestaudio',
    'quiet': True,
    'no_warnings': True,
    'extractor_args': {'youtube': {'player_client': ['web_music']}},
}

cookies_path = os.getenv("COOKIES_FILE", "cookies.txt")
if os.path.exists(cookies_path):
    YDL_OPTS['cookiefile'] = cookies_path

async def resolve_url(video_id: str) -> dict:
    url = f"https://music.youtube.com/watch?v={video_id}"
    loop = asyncio.get_event_loop()
    
    def _extract():
        with yt_dlp.YoutubeDL(YDL_OPTS) as ydl:
            info = ydl.extract_info(url, download=False)
            fmt = info.get('requested_formats', [info])[0]
            
            expires = None
            if 'expire=' in fmt.get('url', ''):
                try:
                    expires = int(fmt['url'].split('expire=')[1].split('&')[0])
                except Exception:
                    pass
            
            return {
                'url': fmt['url'],
                'ext': fmt.get('ext', 'm4a'),
                'abr': fmt.get('abr', 0),
                'expires': expires,
                'videoId': video_id
            }
            
    return await loop.run_in_executor(None, _extract)

async def stream_audio(video_id: str, range_header: str = None):
    data = await resolve_url(video_id)
    cdn_url = data['url']
    
    headers = {'Range': range_header} if range_header else {}
    
    async def _stream():
        async with httpx.AsyncClient(timeout=30) as client:
            async with client.stream('GET', cdn_url, headers=headers) as r:
                async for chunk in r.aiter_bytes(chunk_size=65536):
                    yield chunk
                    
    return StreamingResponse(
        _stream(),
        media_type='audio/mp4',
        headers={
            'Accept-Ranges': 'bytes',
            'Access-Control-Allow-Origin': '*',
        }
    )
