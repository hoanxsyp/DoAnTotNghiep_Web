# Thử nghiệm PhoBERT cho dự đoán giá thuê

Thử nghiệm ngày 16/11/2026, chạy trên CPU với `vinai/phobert-base-v2`. Mục đích là kiểm tra một cấu hình Transformer trên cùng dữ liệu và tập kiểm tra của LightGBM đang dùng. Thử nghiệm không thay đổi mô hình phục vụ API.

## Cách chạy

Từ thư mục `ai_rental`:

```powershell
python -m pip install torch transformers underthesea
python training/compare_phobert.py --epochs 3 --batch-size 8 --max-length 160 --train-last-layers 2
```

Dữ liệu là `data/processed/hanoi_all_clean.csv` với 5.175 tin và 29 đặc trưng đang dùng trong `train_compare.py`. Không đưa `title` hoặc `description` vào PhoBERT, vì văn bản tin đăng có thể chứa giá thuê và làm sai lệch phép đo. Các đặc trưng bảng được viết thành câu tiếng Việt ngắn rồi tách từ bằng `underthesea` trước khi token hóa. Độ dài lớn nhất là 150 token, nên không bản ghi nào bị cắt ở giới hạn 160 token.

Tập test gồm 969 tin, tách theo `listing_group` bằng `GroupShuffleSplit(test_size=0.2, random_state=42)`. Trong 4.206 tin còn lại, 3.742 tin dùng để fit PhoBERT và 464 tin làm validation theo nhóm. Mô hình nạp trọng số PhoBERT pretrained, giữ cố định phần lớn backbone và cập nhật 2 lớp Transformer cuối cùng cùng đầu hồi quy trong 3 epoch. Chọn epoch có MAE validation thấp nhất; chỉ sau đó mới đánh giá tập test. LightGBM được chạy lại với đúng cấu hình từ `models/hanoi_all/metadata.json`. Có thêm mốc LightGBM fit trên đúng 3.742 tin để kiểm tra ảnh hưởng của số lượng mẫu train.

## Kết quả trên cùng 969 tin test

Giá trị MAE và RMSE tính bằng **triệu đồng/tháng**.

| Mô hình | Số tin fit | MAE | RMSE | R² | MAPE |
| --- | ---: | ---: | ---: | ---: | ---: |
| LightGBM hiện tại | 4.206 | 0,449 | 0,664 | 0,749 | 15,23% |
| LightGBM cùng số tin fit | 3.742 | 0,458 | 0,669 | 0,745 | 15,54% |
| PhoBERT, epoch 3 | 3.742 | 0,848 | 1,136 | 0,264 | 32,27% |

MAE validation của PhoBERT qua 3 epoch lần lượt là 1,062; 0,994; 0,868 triệu đồng. Cả ba chỉ số đều cải thiện, nhưng PhoBERT ở cấu hình này vẫn có MAE test cao hơn LightGBM hiện tại **0,400 triệu đồng, tương đương 89,1%**. So với LightGBM fit trên cùng 3.742 tin, MAE vẫn cao hơn 85,3%. Toàn bộ lượt chạy mất 52,8 phút trên CPU.

**Kết luận cho lần thử này:** chưa có cơ sở thay LightGBM bằng PhoBERT. Đây là một cấu hình cụ thể, không phải bằng chứng rằng mọi mô hình Transformer đều kém hơn trên bài toán này. PhoBERT được huấn luyện trước cho văn bản tiếng Việt, còn dữ liệu dự đoán giá hiện tại chủ yếu là số và biến phân loại; việc biến số thành câu chữ có thể không tận dụng được thế mạnh của nó.

**Giới hạn của phép đo:** các đặc trưng giá khu vực trong CSV được tạo trước bước chia train/test. Tiền xử lý có loại bản ghi đang xét khỏi thống kê khu vực, nhưng thống kê của mỗi bản ghi vẫn có thể dùng thông tin từ các bản ghi nằm ở tập test. Vì cả hai mô hình dùng cùng CSV nên phép so sánh tương đối vẫn nhất quán; các chỉ số tuyệt đối có thể lạc quan so với tình huống dự đoán tin mới. Chưa thực hiện tìm kiếm siêu tham số rộng hoặc đánh giá theo thời gian cho PhoBERT.

Số liệu đầy đủ và cấu hình chạy nằm trong [`phobert_comparison.json`](phobert_comparison.json); mã thử nghiệm nằm trong [`training/compare_phobert.py`](../training/compare_phobert.py).
