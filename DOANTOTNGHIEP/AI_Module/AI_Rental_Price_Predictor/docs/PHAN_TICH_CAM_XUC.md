# Sentiment Analysis API

API Flask để phân tích cảm xúc văn bản tiếng Việt sử dụng mô hình BiLSTM.

## Cài đặt và Chạy

### Cách 1: Chạy trực tiếp (Development)

1. Cài đặt dependencies:
```bash
pip install -r requirements.txt
```

2. Đảm bảo có file `phan_tich_cam_xuc.keras` và `tokenizer.pkl` trong thư mục.

3. Chạy API:
```bash
python sentiment_api.py
```

### Cách 2: Chạy với Docker (Production)

1. **Build image**:
```bash
docker build -t sentiment-api .
```

2. **Chạy container**:
```bash
docker run -p 5000:5000 sentiment-api
```

API sẽ chạy trên http://localhost:5000

## Sử dụng

### Kiểm tra trạng thái
```bash
curl http://localhost:5000/health
```

### Dự đoán cảm xúc
```bash
curl -X POST http://localhost:5000/predict \
  -H "Content-Type: application/json" \
  -d '{"text": "Sản phẩm rất tốt, tôi rất hài lòng!"}'
```

Response:
```json
{
  "sentiment": "positive",
  "confidence": 0.9876,
  "message": "Phản hồi tích cực (độ tin cậy: 0.9876)"
}
```

## Tích hợp với Spring Boot

Trong Spring Boot, sử dụng RestTemplate hoặc WebClient để gọi API:

```java
@RestController
public class SentimentController {

    @Autowired
    private RestTemplate restTemplate;

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeSentiment(@RequestBody Map<String, String> request) {
        String text = request.get("text");

        // Gọi API Python
        Map<String, Object> apiRequest = new HashMap<>();
        apiRequest.put("text", text);

        ResponseEntity<Map> response = restTemplate.postForEntity(
            "http://localhost:5000/predict",
            apiRequest,
            Map.class
        );

        return ResponseEntity.ok(response.getBody());
    }
}
```

## Lưu ý

- API chạy trên port 5000, có thể thay đổi trong code.
- Đảm bảo mô hình và tokenizer đã được train và lưu trước.
- Với Docker, container sẽ tự động expose port 5000.