#!/bin/bash
echo "Starting Fusic servers locally (without Docker)..."

# Ensure we are in the correct directory
cd "$(dirname "$0")"

# Start Proxy on 8001
cd proxy
../.venv/bin/pip install -r requirements.txt
../.venv/bin/uvicorn main:app --port 8001 --host 0.0.0.0 &
PROXY_PID=$!
cd ..

# Start API on 8002
cd api
../.venv/bin/pip install -r requirements.txt
../.venv/bin/uvicorn main:app --port 8002 --host 0.0.0.0 &
API_PID=$!
cd ..

# Start Frontend on 8000
cd frontend
python3 -m http.server 8000 &
FRONTEND_PID=$!
cd ..

echo "==============================================="
echo "Servers are running."
echo "Frontend: http://localhost:8000"
echo "API:      http://localhost:8002"
echo "Proxy:    http://localhost:8001"
echo "Press Ctrl+C to stop all servers."
echo "==============================================="

trap "kill $PROXY_PID $API_PID $FRONTEND_PID 2>/dev/null" EXIT
wait
