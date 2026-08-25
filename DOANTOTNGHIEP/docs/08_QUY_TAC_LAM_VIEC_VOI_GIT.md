# Quy tắc làm việc với Git

> Mục tiêu của tài liệu này là thống nhất cách đặt tên nhánh, viết commit và merge code để lịch sử Git dễ đọc, dễ review và dễ rollback khi có lỗi.

---

## 1. Nguyên tắc chung

- Không sửa trực tiếp trên nhánh `main`.
- Mỗi nhánh chỉ nên phục vụ một mục đích rõ ràng: một tính năng, một bug, một refactor hoặc một phần tài liệu.
- Commit nên nhỏ, có ý nghĩa và không gom nhiều việc không liên quan.
- Không commit file chứa secret: `.env`, password, token, private key, file dump database thật.
- Trước khi push, luôn kiểm tra `git status`, `git diff` và chạy test/build liên quan nếu có thể.
- Không sửa lịch sử của nhánh dùng chung nếu chưa thống nhất với cả nhóm.

---

## 2. Quy tắc đặt tên nhánh

### 2.1. Format

Dùng format:

```text
<type>/<mo-ta-ngan>
```

Nếu có mã task/ticket:

```text
<type>/<ticket>-<mo-ta-ngan>
```

Ví dụ:

```text
feature/listing-filter-price
feature/WTR-102-listing-filter-price
fix/auth-refresh-token-expired
hotfix/payment-callback-500
refactor/listing-service-validation
docs/git-workflow-rules
test/auth-service-unit-test
chore/update-docker-compose
release/v1.0.0
```

### 2.2. Các loại nhánh nên dùng

| Type | Khi nào dùng | Ví dụ |
| --- | --- | --- |
| `feature` | Thêm tính năng mới | `feature/save-listing` |
| `fix` | Sửa bug thông thường | `fix/search-district-filter` |
| `hotfix` | Sửa lỗi khẩn cấp trên bản đang chạy | `hotfix/login-500` |
| `refactor` | Đổi cấu trúc code nhưng không đổi hành vi | `refactor/payment-service` |
| `docs` | Sửa hoặc thêm tài liệu | `docs/api-contract` |
| `test` | Thêm/sửa test | `test/listing-controller` |
| `chore` | Việc phụ trợ: config, dependency, script | `chore/update-env-example` |
| `release` | Chuẩn bị phiên bản release | `release/v1.0.0` |

### 2.3. Quy ước bắt buộc

- Tên nhánh dùng chữ thường, không dấu tiếng Việt, không khoảng trắng.
- Dùng dấu gạch ngang `-` để ngăn cách từ.
- Tên nhánh nên ngắn nhưng đủ nghĩa, khoảng 3-6 từ là tốt.
- Không đặt tên chung chung như `fix`, `update`, `test`, `new-feature`, `hoa-branch`.
- Nếu làm tiếp trên nhánh của người khác, cần thống nhất trước để tránh ghi đè lịch sử commit.

---

## 3. Quy tắc viết commit

### 3.1. Format commit

Dùng Conventional Commits bản rút gọn:

```text
<type>(<scope>): <noi-dung-ngan-gon>
```

Trong đó:

- `type`: loại thay đổi.
- `scope`: phạm vi thay đổi, có thể bỏ qua nếu commit nhỏ.
- `noi-dung-ngan-gon`: mô tả kết quả của commit.

Ví dụ:

```text
feat(listing): add price range filter
fix(auth): refresh token before expiry
docs(git): add branch and commit rules
refactor(payment): extract momo callback validator
test(search): add district filter cases
chore(docker): update mysql healthcheck
```

### 3.2. Các `type` commit nên dùng

| Type | Ý nghĩa |
| --- | --- |
| `feat` | Thêm tính năng mới |
| `fix` | Sửa bug |
| `docs` | Thay đổi tài liệu |
| `style` | Định dạng code, không đổi logic |
| `refactor` | Tái cấu trúc code, không đổi hành vi |
| `perf` | Cải thiện hiệu năng |
| `test` | Thêm/sửa test |
| `build` | Thay đổi build tool, dependency, Docker |
| `ci` | Thay đổi CI/CD |
| `chore` | Việc phụ trợ không ảnh hưởng logic sản phẩm |
| `revert` | Đảo ngược commit trước đó |

### 3.3. Scope gợi ý cho dự án

Nên dùng scope theo module hoặc khu vực thay đổi:

```text
auth
user
listing
search
catalog
interaction
moderation
payment
notification
ai
admin
frontend
backend
docker
docs
```

Ví dụ:

```text
fix(payment): handle missing momo signature
feat(ai): call rental price prediction service
fix(frontend): keep search params after pagination
docs(api): update listing response example
```

### 3.4. Quy ước nội dung commit

- Ưu tiên viết commit bằng tiếng Anh ngắn gọn để đồng nhất với tooling và lịch sử Git.
- Nếu cả nhóm thống nhất viết tiếng Việt thì vẫn được, nhưng phải rõ nghĩa và nhất quán.
- Dòng đầu commit nên tối đa khoảng 72 ký tự.
- Commit message nên nói rõ kết quả thay đổi, không chỉ ghi thao tác mơ hồ.
- Không viết commit kiểu `update code`, `fix bug`, `done`, `commit lan 1`, `sua linh tinh`, `WIP`.

Nếu commit cần giải thích thêm, viết body:

```text
fix(auth): rotate refresh token near expiry

Refresh token is renewed when it has less than 15 minutes left.
This keeps long-running user sessions from logging out unexpectedly.
```

---

## 4. Ví dụ đúng và sai

### Nên dùng

```text
feat(listing): add room type filter
fix(search): return empty page for invalid district
refactor(auth): move jwt parsing to token service
docs(readme): add docker startup guide
test(payment): cover momo callback validation
chore(env): sync env example with application config
```

### Không nên dùng

```text
update
fix
fix bug
abc
commit moi
sua file
done task
```

Các commit sai ở trên không cho biết thay đổi nằm ở đâu, để làm gì và khi rollback sẽ rất khó truy vết.

---

## 5. Quy trình làm việc đề xuất

### 5.1. Bắt đầu task mới

```bash
git checkout main
git pull origin main
git checkout -b feature/listing-filter-price
```

### 5.2. Trong lúc làm

Kiểm tra thay đổi:

```bash
git status
git diff
```

Commit từng phần hợp lý:

```bash
git add <file-can-commit>
git commit -m "feat(listing): add price range filter"
```

### 5.3. Trước khi push

```bash
git status
git pull --rebase origin main
```

Sau đó chạy test/build liên quan nếu có:

```bash
# Backend
./mvnw test

# Frontend
npm run build
```

Nếu dự án đang chạy qua Docker và máy local không có JDK/Node, có thể build/test trong container theo hướng dẫn riêng của dự án.

### 5.4. Push nhánh

```bash
git push origin feature/listing-filter-price
```

### 5.5. Tạo Pull Request / Merge Request

PR nên có:

- Mục đích thay đổi.
- Các file/module chính bị ảnh hưởng.
- Cách đã test.
- Ảnh chụp màn hình nếu thay đổi UI.
- Ghi chú migration/config nếu có.

Không merge khi:

- Còn conflict.
- Build/test liên quan đang lỗi.
- Có file secret hoặc file local bị commit nhầm.
- PR gom quá nhiều việc không liên quan.

---

## 6. Merge, rebase và force push

- Ưu tiên `git pull --rebase origin main` trên nhánh cá nhân để lịch sử commit gọn hơn.
- Khi PR đã được review, chọn cách merge theo chính sách nhóm:
  - `Squash merge`: lịch sử `main` gọn, phù hợp nhóm nhỏ.
  - `Merge commit`: giữ lịch sử nhánh, phù hợp task lớn cần truy vết.
  - `Rebase merge`: lịch sử thẳng, cần kỷ luật cao hơn.
- Không `force push` lên `main`, `develop`, `release/*` hoặc nhánh của người khác.
- Nếu bắt buộc sửa lịch sử trên nhánh cá nhân, dùng:

```bash
git push --force-with-lease
```

Không dùng `git push --force` nếu không có lý do rõ ràng.

---

## 7. Xử lý conflict

Khi có conflict:

1. Đọc kỹ file conflict, không chọn máy móc `ours` hoặc `theirs`.
2. Giữ logic mới nhất của cả hai phía nếu chúng không loại trừ nhau.
3. Chạy lại test/build liên quan sau khi resolve.
4. Commit resolve conflict với message rõ ràng nếu cần:

```text
fix(listing): resolve conflict in listing validation
```

Nếu conflict nằm trong migration database, seed data, config bảo mật hoặc file có ảnh hưởng lớn, nên nhờ người cùng làm module đó review lại.

---

## 8. Quy tắc với file nhạy cảm và file local

Không commit:

- `.env`
- File chứa password, token, private key, secret key.
- File build output: `dist/`, `target/`, `build/`, `node_modules/`.
- File IDE/local: `.idea/`, `.vscode/` nếu không phải config chung của team.
- Log, cache, file tạm.
- Database dump thật nếu có dữ liệu người dùng.

Nếu cần chia sẻ biến môi trường mới, cập nhật `.env.example` thay vì commit `.env`.

Ví dụ commit phù hợp:

```text
chore(env): add payment callback env examples
```

---

## 9. Checklist trước khi merge

- Nhánh được đặt tên đúng format.
- Commit message đọc được và có ý nghĩa.
- Không còn file thừa trong `git status`.
- Đã pull/rebase với `main` mới nhất.
- Đã chạy test/build liên quan hoặc ghi rõ lý do chưa chạy.
- Không commit secret, file local, file build output.
- PR mô tả rõ thay đổi và cách kiểm tra.
- Nếu có thay đổi API/database/config, đã cập nhật tài liệu hoặc `.env.example`.

---

## 10. Tóm tắt nhanh

Tên nhánh:

```text
feature/listing-filter-price
fix/auth-refresh-token-expired
docs/git-workflow-rules
```

Commit:

```text
feat(listing): add price range filter
fix(auth): refresh token before expiry
docs(git): add branch and commit rules
```

Quy tắc quan trọng nhất: lịch sử Git phải giúp người khác hiểu được "đã thay đổi cái gì, ở đâu, vì sao" mà không cần mở từng dòng code.
