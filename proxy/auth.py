from fastapi import Header, HTTPException
from dotenv import load_dotenv
import os

load_dotenv()
API_KEY = os.getenv('API_KEY')

async def verify_key(x_api_key: str = Header(...)):
    if not API_KEY:
        raise HTTPException(500, 'API_KEY not set')
    if x_api_key != API_KEY:
        raise HTTPException(401, 'Invalid API key')
