# Re-link — Thiết kế (bước 3 của 3)

| Bước | Chức năng | Kết quả |
|---|---|---|
| 1 | Resource Plan (docs/resource-plan-design.md) | Danh sách người theo role, level, kỹ năng, khoảng thời gian tham gia |
| 2 | Assignment (docs/assignment-design.md) | Mỗi task hoạt động có đúng một người làm |
| 3 | **Re-link** (tài liệu này) | Nối task của mỗi người thành chuỗi, ProjectLibre tự tính lịch |

**Re-link là phần *chuỗi* của Auto Link, không có menu riêng.** Sau khi phân công, chạy lại Auto Link là xong bước 3.

**Bước này chỉ quyết định *thứ tự* task của mỗi người, không tự đặt ngày.** Ngày do CPM của ProjectLibre tính từ link
(memory: không dùng `levelingDelay`). Kết quả nhìn thấy được trên Gantt, sửa tay được, chạy lại được.

## 1. Phạm vi

- Các task hoạt động (GP, PT, KT; sau này UT), trong cả dự án hoặc dưới các task đang chọn.
- Task cancel hoặc không có work: bỏ qua, các task hai bên nối với nhau.
- Task đã hoàn thành, đang làm: vẫn nằm trong chuỗi (thường đứng đầu), để task sau không bắt đầu trước khi nó xong.
- Ngoài phạm vi: đổi người (bước 2), thêm người (bước 1), tách task, resource leveling.

## 2. Hai loại link

| Loại | Ý nghĩa | Phần của Auto Link |
|---|---|---|
| Logic | GP → PT → KT trong một chức năng/dòng; link làm tay | *Link giữa các hạng mục* |
| Nguồn lực | Task trước → task sau **của cùng một người** | *Link nối tiếp các công việc của mỗi người* |

Phần chuỗi của Auto Link đổi như sau:

| | Trước | Bây giờ |
|---|---|---|
| Task đã giao | Một chuỗi cho mỗi (activity, người) | Một chuỗi cho mỗi **người** (DEV làm cả PT và UT vẫn một chuỗi, không chồng nhau) |
| Task chưa giao | Một chuỗi cho mỗi (activity, kỹ năng yêu cầu) | Mặc định **không nối**, cảnh báo số task; tuỳ chọn nối như trước (dùng trước khi phân công) |
| Thứ tự | WBS | Sẵn sàng sớm nhất hoặc WBS (mục 3) |

- Link tạo ra là `isAuto()`: chạy lại thì xoá và tạo lại. Link làm tay không bị đụng; link làm tay trùng với link cần
  tạo thì giữ (đếm là *đã có*).
- Task nhiều người (sửa tay): nằm trong chuỗi của từng người.

## 3. Thứ tự task của mỗi chuỗi

### 3.1 Sẵn sàng sớm nhất (mặc định)

Mô phỏng nhanh để lấy **thứ tự**, không ghi ngày:

1. Thời lượng của task = thời lượng hiện tại (Work / units của phân công), tính theo ngày làm việc của lịch.
2. Task *sẵn sàng* khi mọi predecessor logic đã xong trong mô phỏng: link giữa các hạng mục, link làm tay tới task hoặc
   task cha của nó (link từ một task cha tính như link từ mọi task được nối bên dưới), task ngoài phạm vi theo ngày
   hiện có. Bắt đầu = max(ngày sẵn sàng, ngày người đó rảnh, ngày người đó vào dự án).
3. Lặp: chọn task có ngày bắt đầu sớm nhất trong các task sẵn sàng, hoà thì theo thứ tự WBS; nối nó sau task trước
   của chuỗi.

Thứ tự lấy từ một lịch hợp lệ nên không tạo vòng. Ví dụ TESTER kiểm thử chức năng A (DEV chậm) và B (DEV nhanh): theo WBS
thì chờ A rồi mới tới B; theo sẵn sàng thì làm B trước, không bị ngồi chờ.

### 3.2 Theo WBS

Nối theo thứ tự WBS, dễ đoán. Nếu task kế tiếp của mọi chuỗi đều chờ một link làm tay đi ngược WBS thì lấy task sẵn
sàng sớm nhất, thay vì tạo vòng.

### 3.3 Sửa tay (R2)

Thứ tự **không lưu riêng: chính là các link**. Đọc thứ tự của mỗi người từ chuỗi hiện có; task mới chèn theo chế độ
đang chọn. Đổi thứ tự bằng Lên/Xuống hoặc kéo thả. Thứ tự tạo vòng với link logic thì tô đỏ, không cho Apply.

## 4. Ngày vào dự án

ProjectLibre không tự dời task tới ngày resource bắt đầu available (auto-schedule-design mục 3.2), nên người TBD vào
dự án ngày 16/11 vẫn có thể được xếp làm từ ngày bắt đầu dự án.

- Task đầu tiên trong chuỗi của mỗi người nhận ràng buộc **Start No Earlier Than = ngày người đó bắt đầu available**
  (đầu khoảng Max Units > 0% đầu tiên), nếu ngày đó sau ngày bắt đầu dự án. Các task sau tự nằm sau nhờ chuỗi.
- Ràng buộc này được đánh dấu (`NormalTask.joinConstraint`, lưu trong .pod): chạy lại thì dời hoặc gỡ. Task có ràng
  buộc người dùng tự đặt (khác ASAP, không có dấu) thì giữ nguyên, không ghi đè.
- Khoảng 0% ở giữa (nghỉ giữa chừng) và làm quá ngày rời dự án: không xử lý, đã có icon của `AvailabilityWarning` ở
  bảng task.

## 5. Dọn dữ liệu lịch cũ

- `levelingDelay` của task và phân công trong phạm vi đặt về 0 (kết quả Auto Schedule P2 cũ nếu có): lịch chỉ từ link
  và ràng buộc.
- Units của phân công giữ nguyên (đã lấy theo Max Units ở bước 2).

## 6. Hộp thoại *Auto Link*

```
Công việc được link:  (•) Toàn bộ dự án  ( ) Các công việc đang chọn
Cách link:
  ☑ Link giữa các hạng mục          Giải pháp → Phát triển [trong cùng dòng ▾] ...
  ☑ Link nối tiếp các công việc của mỗi người
       (•) Sẵn sàng sớm nhất   ( ) Theo thứ tự WBS
       ☐ Link cả công việc chưa giao (theo kỹ năng yêu cầu / hạng mục)
       ☑ Công việc đầu tiên của mỗi người không sớm hơn ngày người đó vào dự án
Xem trước:
  631 công việc được link: sẽ tạo 1363 link, xóa 0 ...
  Công việc của 9 người. Ràng buộc ngày vào dự án: đặt 7, gỡ 0. Xóa leveling delay của 1 công việc.
  Kết thúc dự kiến 11/02/2027. Deadline 19/04/2027: kịp, còn 47 ngày làm việc.
  ⚠ 5 công việc chưa giao: không được link theo người.
```

- Ngày dự kiến lấy từ mô phỏng (mục 3.1). Sau khi áp dụng, nếu có chuỗi thì báo ngày kết thúc thật của dự án so với
  deadline của Resource Plan (kịp / kịp nhưng lấn buffer / trễ bao nhiêu ngày làm việc).
- Cả lần áp dụng (link, ràng buộc, `levelingDelay`) là một bước Undo, tính lịch một lần ở cuối (`AutoLinker`).

## 7. Lộ trình

| Giai đoạn | Nội dung |
|---|---|
| R1 | Chuỗi theo người trong Auto Link (sẵn sàng sớm nhất / WBS), tuỳ chọn nối task chưa giao, ràng buộc ngày vào dự án, xoá `levelingDelay`, ngày kết thúc so với deadline |
| R2 | Bảng người / task của người, ngày dự kiến và thật, số ngày chờ, đổi thứ tự tay |
| R3 | Chỗ nghẽn (người có task trên đường găng), cảnh báo quá ngày Đến; xuất Excel lịch theo người (`XlsxSheetWriter`) |

## 8. Đã chốt

1. Gộp vào Auto Link, không có menu Re-link riêng.
2. Thứ tự mặc định: **sẵn sàng sớm nhất**; WBS là tuỳ chọn.
3. Ngày vào dự án: **ràng buộc Start No Earlier Than** trên task đầu chuỗi, có dấu.
4. Task chưa giao: mặc định **không nối**, cảnh báo số task; tuỳ chọn nối theo kỹ năng yêu cầu như trước.

## 9. Ghi chú hiện thực (R1)

- `AutoLinkPlan` lấy khoá chuỗi của mỗi task (người, hoặc activity + kỹ năng nếu nối task chưa giao), `PersonSequencer`
  mô phỏng ra thứ tự, rồi tính ràng buộc ngày vào và các task có `levelingDelay`. `AutoLinker` ghi chúng cùng
  transaction và bước Undo với link.
- Thử trên một dự án 631 task, 9 người: ước lượng trùng ngày ProjectLibre tính sau khi áp dụng, không ai làm hai task
  cùng lúc, không task nào trước ngày vào; sẵn sàng sớm nhất kết thúc sớm hơn WBS 6 ngày làm việc.
- `calendar.compare()` của ProjectLibre đếm thừa một ngày làm việc với mốc có lẻ giây: số ngày so với deadline tính trên
  mốc làm tròn tới phút (`FinishSummary`).
