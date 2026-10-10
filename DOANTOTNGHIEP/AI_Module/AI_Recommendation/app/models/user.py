from pydantic import BaseModel, Field
from typing import Literal, Optional
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
    session_id: Optional[str] = None
    duration_seconds: Optional[int] = Field(default=None, ge=0)


class InteractionEvent(BaseModel):
    user_id: str
    room_id: str
    event_type: Literal["view", "favorite", "unfavorite"]
    occurred_at: Optional[datetime] = None
    session_id: Optional[str] = None
    duration_seconds: Optional[int] = Field(default=None, ge=0)
