from abc import ABC, abstractmethod

class DataProvider(ABC):

    @abstractmethod
    def get_all_rooms(self) -> list[dict]: ...

    @abstractmethod
    def get_room_by_id(self, room_id: str) -> dict | None: ...

    @abstractmethod
    def get_user(self, user_id: str) -> dict | None: ...

    @abstractmethod
    def get_view_history(self, user_id: str, limit: int = 50) -> list[dict]: ...

    @abstractmethod
    def save_view_event(self, event: dict) -> None: ...
