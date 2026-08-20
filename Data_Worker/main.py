from fastapi import FastAPI
import random
import asyncio

app = FastAPI()


@app.get("/")
def read_root():
    return {"status": "Data Worker is running"}


@app.get("/fetch-market")
async def fetch_market_data():
    # TODO: 나중에 실제 증권사 API를 호출하는 로직이 들어갈 자리
    await asyncio.sleep(1)  # 네트워크 지연 시뮬레이션
    mock_price = round(random.uniform(100, 200), 2)
    return {"symbol": "AAPL", "fetched_price": mock_price}
