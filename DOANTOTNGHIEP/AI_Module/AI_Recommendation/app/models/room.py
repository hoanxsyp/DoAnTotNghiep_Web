from pydantic import BaseModel
from typing import Optional

class Room(BaseModel):
    id: str
    title: str
    price: float
    area: float
    district: str
    city: str
    room_type: str
    amenities: list[str]
    lat: float
    lng: float
    is_available: bool


class RoomRecommendation(BaseModel):
    room: Room
    score: float
    is_cold_start: bool
