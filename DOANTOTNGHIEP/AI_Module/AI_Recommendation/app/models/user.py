from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class User(BaseModel):
    id: str
    email: str
    city: Optional[str] = None
    district: Optional[str] = None


class ViewEvent(BaseModel):
    user_id: str
    room_id: str
    viewed_at: Optional[datetime] = None
