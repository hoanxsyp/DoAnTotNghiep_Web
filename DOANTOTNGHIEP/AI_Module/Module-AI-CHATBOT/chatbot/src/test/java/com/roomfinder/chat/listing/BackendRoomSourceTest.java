package com.roomfinder.chat.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomfinder.chat.config.ListingSourceProperties;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.model.Filters;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kiểm thử nguồn tin đăng của web — API backend giả lập, không cần backend chạy. */
class BackendRoomSourceTest {

    private final ObjectMapper om = new ObjectMapper();
    private final WebtroApiClient api = mock(WebtroApiClient.class);
    private final WebtroCatalog catalog = mock(WebtroCatalog.class);
    private final BackendRoomSource source = new BackendRoomSource(api, catalog, new ListingSourceProperties());

    private JsonNode json(String s) {
        try {
            return om.readTree(s.replace('\'', '"'));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode page(long... ids) {
        StringBuilder sb = new StringBuilder("{'items':[");
        for (int i = 0; i < ids.length; i++) sb.append(i > 0 ? "," : "").append("{'id':").append(ids[i]).append('}');
        return json(sb.append("]}").toString());
    }

    private JsonNode detail(long id, long wardId, String amenities) {
        return json("{'id':" + id + ",'slug':'phong-" + id + "','title':'Phòng " + id + "','price':2500000.0,"
                + "'area':20.0,'districtName':'Quận Cầu Giấy','fullAddress':'Số 1, Dịch Vọng','description':'mô tả',"
                + "'wardId':" + wardId + ",'latitude':null,'longitude':null,'categoryCode':'MINI_APARTMENT',"
                + "'toiletType':'PRIVATE','parkingAvailable':false,'amenities':[" + amenities + "],"
                + "'images':[{'url':'u1','thumbnailUrl':'t1','primary':false},{'url':'u2','thumbnailUrl':'t2','primary':true}]}");
    }

    private void stubDetail(long id, JsonNode d) {
        when(api.getData(URI.create("/api/listings/" + id + "?countView=false"))).thenReturn(d);
    }

    @Test
    void mapsFiltersToBackendQueryParams() {
        when(catalog.resolveArea("Cầu Giấy")).thenReturn(new WebtroCatalog.Area(1L, 105L, null));
        when(catalog.amenityId("WIFI")).thenReturn(19L);
        when(catalog.amenityId("AIR_CONDITIONER")).thenReturn(1L);
        when(catalog.amenityId("WATER_HEATER")).thenReturn(2L);
        when(api.getData(argThat(u -> u != null && u.getPath().equals("/api/search/listings")))).thenReturn(page());

        Filters f = new Filters();
        f.setPriceMax(3_000_000L);
        f.setLocation("Cầu Giấy");
        f.setRoomType("CHUNG_CU_MINI");
        f.setUtilities(new java.util.ArrayList<>(List.of("air_conditioner", "wifi", "water_heater",
                "private_bathroom", "pet", "parking")));
        source.search(f, null, null, 0, 5);

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(api).getData(uri.capture());
        String q = uri.getValue().getQuery();
        assertTrue(q.contains("priceTo=3000000"), q);
        assertTrue(q.contains("provinceId=1") && q.contains("districtId=105"), q);   // backend bắt buộc đủ phân cấp
        assertFalse(q.contains("wardId"), q);
        // tiện ích thuộc bảng amenities → amenityIds (backend lọc "có ĐỦ")
        assertTrue(q.contains("amenityIds=1") && q.contains("amenityIds=19") && q.contains("amenityIds=2"), q);
        // thuộc tính là cột riêng của tin → tham số riêng
        assertTrue(q.contains("toiletType=PRIVATE") && q.contains("petAllowed=true")
                && q.contains("parkingAvailable=true"), q);
        assertTrue(q.contains("categoryCode=MINI_APARTMENT") && q.contains("categoryCode=APARTMENT"), q);
        assertTrue(q.contains("sort=price,asc") && q.contains("size=100"), q);
        assertFalse(q.contains("SMALL_PREMISES"), q);
    }

    @Test
    void withoutRoomTypeSearchesResidentialCategoriesOnly() {
        when(api.getData(argThat(u -> u != null && u.getPath().equals("/api/search/listings")))).thenReturn(page());
        source.search(new Filters(), null, null, 0, 5);

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(api).getData(uri.capture());
        String q = uri.getValue().getQuery();
        assertTrue(q.contains("categoryCode=BOARDING_HOUSE") && q.contains("categoryCode=WHOLE_HOUSE"), q);
        assertFalse(q.contains("SMALL_PREMISES"), q);
    }

    @Test
    void sortsByPriceLocallyAndFetchesDetailsOnlyForTopK() {
        // Backend trả tin ĐẨY lên đầu (giá 5tr) rồi mới tới giá tăng dần.
        when(api.getData(argThat(u -> u != null && u.getPath().equals("/api/search/listings"))))
                .thenReturn(json("{'items':[{'id':5,'price':5000000.0},{'id':1,'price':1500000.0},"
                        + "{'id':2,'price':2000000.0}]}"));
        stubDetail(1, detail(1, 11, ""));
        stubDetail(2, detail(2, 11, ""));

        List<Room> rooms = source.search(new Filters(), null, null, 0, 2);

        assertEquals(List.of(1L, 2L), rooms.stream().map(Room::getId).toList());
        verify(api, times(0)).getData(URI.create("/api/listings/5?countView=false"));   // không lấy thừa
    }

    @Test
    void unknownLocationFallsBackToKeyword() {
        when(api.getData(argThat(u -> u != null && u.getPath().equals("/api/search/listings")))).thenReturn(page());
        Filters f = new Filters();
        f.setLocation("Trần Duy Hưng");
        source.search(f, null, null, 0, 5);

        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(api).getData(uri.capture());
        assertTrue(uri.getValue().getQuery().contains("keyword=Trần Duy Hưng"), uri.getValue().getQuery());
    }

    @Test
    void geoSearchFiltersByCircleAndSortsByDistance() {
        when(api.getData(argThat(u -> u != null && u.getPath().equals("/api/search/listings"))))
                .thenReturn(page(1, 2, 3));
        stubDetail(1, detail(1, 11, ""));
        stubDetail(2, detail(2, 12, ""));
        stubDetail(3, detail(3, 13, ""));
        when(catalog.wardPoint(11L)).thenReturn(new WebtroCatalog.Point(21.010, 105.800)); // ~1.1km
        when(catalog.wardPoint(12L)).thenReturn(new WebtroCatalog.Point(21.001, 105.800)); // ~0.1km
        when(catalog.wardPoint(13L)).thenReturn(new WebtroCatalog.Point(21.030, 105.800)); // ~3.3km -> ngoài 2km

        List<Room> rooms = source.search(new Filters(), 21.000, 105.800, 2000, 5);

        assertEquals(List.of(2L, 1L), rooms.stream().map(Room::getId).toList());
        assertTrue(rooms.get(0).getDistanceM() < rooms.get(1).getDistanceM());
        ArgumentCaptor<URI> uri = ArgumentCaptor.forClass(URI.class);
        verify(api, atLeastOnce()).getData(uri.capture());
        String q = uri.getAllValues().get(0).getQuery();
        assertTrue(q.contains("radiusKm=2.0") && q.contains("latitude=21.0") && q.contains("size=100"), q);
    }

    @Test
    void mapsDetailToRoomAndCachesIt() {
        stubDetail(7, detail(7, 11, "{'code':'AIR_CONDITIONER','name':'Máy lạnh'},{'code':'MOTORBIKE_PARKING','name':'Chỗ để xe máy'}"));
        when(catalog.wardPoint(11L)).thenReturn(new WebtroCatalog.Point(21.0, 105.8));

        Room r = source.findByIds(List.of(7L)).get(0);
        source.findByIds(List.of(7L));   // lần 2 lấy từ cache

        assertEquals("phong-7", r.getSlug());
        assertEquals(2_500_000L, r.getPrice());
        assertTrue(r.getHasAirConditioner());
        assertTrue(r.getHasParking());          // từ tiện ích MOTORBIKE_PARKING
        assertFalse(r.getHasWifi());
        assertTrue(r.getIsPrivateBathroom());
        assertEquals("CHUNG_CU_MINI", r.getRoomType());
        assertEquals("t2", r.getThumbnail());   // ảnh primary
        assertEquals(List.of("Máy lạnh", "Chỗ để xe máy"), r.getAmenityNames());
        assertEquals(21.0, r.getLatitude().doubleValue(), 1e-9);   // tâm phường vì API ẩn tọa độ
        verify(api, times(1)).getData(URI.create("/api/listings/7?countView=false"));
    }

    @Test
    void skipsListingsThatAreNoLongerPublic() {
        stubDetail(8, null);   // 404: tin đã ẩn/hết hạn
        stubDetail(9, detail(9, 11, ""));
        assertEquals(List.of(9L), source.findByIds(List.of(8L, 9L)).stream().map(Room::getId).toList());
        verify(api, times(2)).getData(any());
    }

    @Test
    void catalogKeyStripsAdministrativePrefixAndAccents() {
        assertEquals("cau giay", WebtroCatalog.key("Quận Cầu Giấy"));
        assertEquals("dich vong", WebtroCatalog.key("Phường Dịch Vọng"));
        assertEquals("cau giay", WebtroCatalog.key("  cầu giấy "));
    }
}
