# Resource Plan — Thiết kế (bước 1 của 3)

Lộ trình mới thay cho Auto Schedule một phát (docs/auto-schedule-design.md):

| Bước | Chức năng | Kết quả |
|---|---|---|
| 1 | **Resource Plan** (tài liệu này) | Danh sách người theo role, level, kỹ năng, khoảng thời gian tham gia |
| 2 | Assignment | Giao task cho người trong Resource Plan |
| 3 | Re-link | Sinh lại chuỗi predecessor theo từng người, ProjectLibre tự tính lịch |

**Bài học từ Auto Schedule:** không dùng `levelingDelay`, không để thuật toán tự xếp ngày. Ngày luôn do ProjectLibre tính
từ link (CPM). Mỗi bước cho ra kết quả nhìn thấy, sửa tay được, chạy lại được.

## 1. Phạm vi

Làm trước: **deadline cố định → cần bao nhiêu người**. Chế độ ngược lại (team cố định → deadline sớm nhất) dùng chung
cách tính, làm ngay sau (mục 7).

| Tình huống | Đầu vào cố định | Tính ra |
|---|---|---|
| Fixed-price, có deadline khách mong muốn | deadline | số người mỗi role/level, khoảng thời gian |
| T&M, khách cần gấp | deadline + team hiện có | người **tăng cường**: role, level, số lượng, từ ngày–đến ngày |
| Fixed-price, deadline không kịp | số người tối đa | deadline sớm nhất để propose (mục 7) |

## 2. Dữ liệu đầu vào

### 2.1 Effort từ ULNL (đã có)

Sheet *NL Chuc nang*, giờ ở point 1.0, lưu vào Original Estimate / Work của task hoạt động:

| Cột | Hoạt động | Role | Kỹ năng |
|---|---|---|---|
| F | GP — Giải pháp | BA | — |
| G | PT — Phát triển | DEV | theo mã chức năng (cột L → BE, FE, Mobile...) |
| I | KT — Kiểm thử | TESTER | — |

**Cột H (Unit test): tạm bỏ qua**, ít dùng. Khi cần sẽ là **một activity riêng** (UT), không gộp vào PT: role DEV, cùng
kỹ năng với PT, thuộc giai đoạn phát triển, nối PT → UT → KT. Lúc đó chỉ cần thêm một task khi phân rã ULNL và cập nhật
Auto Link; Resource Plan tự tính UT vào effort DEV vì gom theo role của activity.

### 2.2 Effort ngoài chức năng — chưa import

Sheet *NL Phi CN* có các đầu việc không gắn với chức năng: QTDA (10%), QA (3%), NT1, NT2, UC3, UC4, KH2, HOP2...
Đây là effort trải đều cả dự án, quyết định số **PM, QA** và một phần BA/TESTER.

**Thiếu 2 — đề xuất:** đọc sheet *NL Phi CN*, map **mã đầu việc → role** bằng một bảng giống *Mã chức năng và Kỹ năng yêu
cầu* (mặc định QTDA, KH2, HOP2 → PM; QA → QA; NT1, NT2, UC4 → TESTER; UC3 → DEV). Không tạo task theo chức năng;
chỉ cộng vào effort của role trong Resource Plan. Có thể tạo task Level of Effort ở bước 2 nếu cần.

Giai đoạn đầu (RP1) cho nhập tay **effort thêm** (MD) cho bất kỳ role nào (PM, QA, BA...) trong bảng effort; import
*NL Phi CN* làm ở RP3.

### 2.3 Bảng tra cứu NLTC (đã có, Labor Category)

role × level → point, đơn giá/MM. Point quy ra Max Units (memory: chỉ dùng field gốc).

## 3. Khái niệm: dòng kế hoạch

Một **dòng** = một nhóm người giống nhau:

| Role | Kỹ năng | Level | Từ ngày | Đến ngày | % phân bổ | Số lượng | Nguồn |
|---|---|---|---|---|---|---|---|
| BA | | 4 - Middle (M) | 02/11/2026 | 29/01/2027 | 100% | 1 | Tăng cường |
| BA | | 2 - Junior (J) | 02/11/2026 | 29/01/2027 | 100% | **auto** → 5 | Tăng cường |
| DEV | BE | 2 - Junior (J) | 16/11/2026 | 12/03/2027 | 100% | auto → 6 | Tăng cường |
| DEV | BE | 4 - Middle (M) | 16/11/2026 | 12/03/2027 | 100% | 2 | Có sẵn (khoá) |
| PM | | 4 - Middle (M) | 02/11/2026 | 26/03/2027 | 50% | 1 | Tăng cường |

- **Số lượng** nhập tay, hoặc **auto**: mỗi nhóm role/kỹ năng có tối đa một dòng auto, dòng đó lấp phần effort còn thiếu.
  Mặc định mỗi role/kỹ năng có một dòng auto ở level chuẩn (BA L2, DEV L2, TESTER L2).
- **Nguồn = Có sẵn**: resource đã có trong dự án (T&M), không đổi số lượng, chỉ tính vào năng lực. Các dòng còn lại là
  **tăng cường** → kết quả "cần thêm bao nhiêu, trong bao lâu".

## 4. Cách tính (theo năng lực, không xếp lịch)

Cho mỗi nhóm role/kỹ năng:

```
năng lực 1 dòng (giờ quy đổi point 1.0) = số lượng × point(level) × % phân bổ × giờ làm việc trong [từ, đến] × hệ số sử dụng
số lượng dòng auto = ceil( (effort − năng lực các dòng khác) / (point × % × giờ làm việc × hệ số sử dụng) )
```

- Giờ làm việc tính theo **lịch dự án** (bỏ ngày nghỉ, lễ).
- **Hệ số sử dụng** (mặc định 85%, cấu hình): không ai làm 100% thời gian, và còn thời gian chờ việc giữa các khâu.
- Hiển thị cho mỗi nhóm: effort, năng lực, **FTE cần** (số lẻ, ví dụ 4.34), số người (làm tròn), % tải thực tế.

Ví dụ: BA effort 400 MD = 3 200 giờ, khoảng 3 tháng = 66 ngày = 528 giờ, hệ số 100%:
1 BA M (1.59) = 839.5 giờ; còn 2 360.5 giờ / (1.03 × 528 = 543.8) = 4.34 → **5 BA J**, tải 87%.

### 4.1 Khoảng thời gian mặc định của mỗi role

Các role không cùng lúc: BA đi trước, TESTER đi sau. Từ ngày bắt đầu S và deadline D (sau khi trừ buffer):

| Role | Từ | Đến |
|---|---|---|
| BA | S | D − đuôi BA |
| DEV | S + độ trễ DEV | D − đuôi kiểm thử |
| TESTER | S + độ trễ TESTER | D |
| PM, QA | S | D |

Độ trễ và đuôi là tham số (ngày làm việc), mặc định: độ trễ DEV 10, độ trễ TESTER 20, đuôi kiểm thử 10, đuôi BA 20.
Sửa trực tiếp ngày trên từng dòng nếu muốn; dòng đã sửa ngày thì không đổi theo tham số nữa.

**Mọi tham số đều sửa được** trên hộp thoại: buffer, hệ số sử dụng, độ trễ, đuôi, số người tối đa mỗi role. Lần dùng
sau nhớ giá trị đã nhập (`Preferences`), có nút *Mặc định* để trả về giá trị gốc.

### 4.2 Kiểm tra khả thi

Đủ năng lực chưa chắc kịp: một task chỉ một người làm (không tách task), nên chuỗi dài nhất của một chức năng là giới
hạn dưới.

- **Chuỗi găng tối thiểu** = max theo chức năng của GP/point_max(BA) + PT/point_max(DEV) + KT/point_max(TESTER),
  theo link GP → PT → KT của Auto Link. Vượt deadline → báo **không khả thi**, liệt kê chức năng gây ra (gợi ý tách
  chức năng trong ULNL).
- Nhóm có **số người vượt giới hạn** (tham số số người tối đa mỗi role) → báo đỏ.
- Đây là ước lượng nhanh. Lịch thật do ProjectLibre tính sau bước 2 (Assignment) và 3 (Re-link).

## 5. Ghi vào dự án

**Resource là nơi lưu duy nhất**, không có bảng kế hoạch riêng:

- Apply: mỗi người của dòng tăng cường thành một resource **Generic (TBD)**, tên `BA J #3 (TBD)`, role, level, kỹ năng như
  dòng, Availability: 0% trước *Từ*, `point × %` trong [Từ, Đến], 0% sau *Đến* (field gốc, P1 đã hỗ trợ nhiều khoảng).
- Mở lại hộp thoại: đọc các resource có role, **gom** theo (role, kỹ năng, level, từ, đến, %, TBD hay có sẵn) → các dòng.
- Apply lại: các resource TBD **chưa có phân công** được xoá và tạo lại theo các dòng (đánh số lại #1, #2...).
  Resource TBD đã có phân công được giữ, tính vào số lượng của dòng cùng nhóm; nếu dòng đó không còn hoặc ít người hơn thì
  báo cảnh báo, không xoá.
- Resource có sẵn không bị sửa.
- Thông số, deadline, effort thêm và **các dòng Auto** (theo role, kỹ năng, level) được lưu trong file dự án: mở lại thì
  dòng Auto vẫn là Auto, đổi deadline là số người tự tính lại.
- Nhóm có effort mà không dòng nào làm được thì luôn được thêm một dòng Auto; muốn bỏ thì đặt số lượng 0.
- Cả lần Apply là một bước Undo.

Nhờ vậy biểu đồ resource của ProjectLibre, bước 2 (Assignment) và báo cáo đều dùng thẳng các resource này.

## 6. Giao diện: hộp thoại *Resource Plan*

Ribbon HTD Planner, nhóm **Planning**, một vùng mới (sau Baseline) với nút **Resource Plan**.

1. **Thông số:** ngày bắt đầu (mặc định Project Start), deadline, buffer, hệ số sử dụng, độ trễ/đuôi, số người tối đa.
2. **Bảng effort:** role, kỹ năng, số task, effort từ WBS (MD), effort thêm (MD, nhập tay, ví dụ PM, QA), tổng (MD, MM với
   1 MM = 22 MD = 176 giờ).
3. **Bảng dòng kế hoạch** (mục 3): thêm/xoá/sửa dòng, cột tính sẵn: point, năng lực, FTE, số người, % tải, chi phí
   (= số người × đơn giá × số tháng trong khoảng).
4. **Kết luận:** khả thi / không khả thi, tổng số người, tổng MM chiếm dụng, tổng chi phí, nhóm thiếu/thừa.
5. Nút Apply, Xuất Excel (RP5).

Tính lại ngay khi sửa, như preview của Auto Link. Nhớ thông số bằng `Preferences`.

## 7. Chế độ ngược: team cố định → deadline sớm nhất

Cùng công thức mục 4, ẩn là D: tìm D nhỏ nhất (tìm nhị phân theo ngày làm việc) sao cho với số người đã nhập (hoặc số
người tối đa) mọi nhóm đủ năng lực trong khoảng mặc định của mình, và D ≥ chuỗi găng tối thiểu.
Kết quả: "Deadline sớm nhất ~ dd/mm/yyyy" để propose với khách.

## 8. Lộ trình

| Giai đoạn | Nội dung |
|---|---|
| RP1 | Hộp thoại Resource Plan (deadline cố định): bảng effort, dòng kế hoạch, tính FTE/số người, kiểm tra khả thi, Apply → resource TBD, đọc lại từ resource |
| RP2 | Chế độ ngược: deadline sớm nhất |
| RP3 | Import *NL Phi CN* + bảng map mã đầu việc → role (PM, QA...) |
| RP4 | Xuất Excel Resource Plan (`XlsxSheetWriter`) |
| sau | Activity Unit test (cột H) + cập nhật Auto Link |

Sau đó: bước 2 Assignment, bước 3 Re-link.
