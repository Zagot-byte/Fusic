import httpx
from fastapi import HTTPException
from fastapi.responses import StreamingResponse

async def proxy_image(url: str):
    async with httpx.AsyncClient(timeout=10) as client:
        r = await client.get(url)
    return StreamingResponse(
        iter([r.content]),
        media_type=r.headers.get('content-type', 'image/jpeg'),
        headers={
            'Cache-Control': 'public, max-age=86400',
            'Access-Control-Allow-Origin': '*',
        }
    )
