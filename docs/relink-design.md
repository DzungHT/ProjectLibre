# Re-link — Thiết kế (bước 3 của 3)

| Bước | Chức năng | Kết quả |
|---|---|---|
| 1 | Resource Plan (docs/resource-plan-design.md) | Danh sách người theo role, level, kỹ năng, khoảng thời gian tham gia |
| 2 | Assignment (docs/assignment-design.md) | Mỗi task hoạt động có đúng một người làm |
| 3 | **Re-link** (tài liệu này) | Nối task của mỗi người thành chuỗi, ProjectLibre tự tính lịch |

**Bước này chỉ quyết định *thứ tự* task của mỗi người, không tự đặt ngày.** Ngày do CPM của ProjectLibre tính từ link
(memory: không dùng `levelingDelay`). Kết quả nhìn thấy được trên Gantt, sửa tay được, chạy lại được.

## 1. Phạm vi

- Các task hoạt động (GP, PT, KT; sau này UT) có người được giao, trong cả dự án hoặc dưới các task đang chọn.
- Task cancel hoặc không có work: bỏ qua, các task hai bên nối với nhau (như Auto Link).
- Task đã hoàn thành, đang làm: vẫn nằm trong chuỗi (thường đứng đầu), để task sau không bắt đầu trước khi nó xong.
- Ngoài phạm vi: đổi người (bước 2), thêm người (bước 1), tách task, resource leveling.

## 2. Hai loại link

| Loại | Ý nghĩa | Ai tạo |
|---|---|---|
| Logic | GP → PT → KT trong một chức năng/dòng; link làm tay | Auto Link (phần *Hoạt động*), người dùng |
| Nguồn lực | Task trước → task sau **của cùng một người** | Re-link |

Re-link = Auto Link với phần *chuỗi* (sequence) đổi như sau:

| | Auto Link hiện tại | Re-link |
|---|---|---|
| Task đã giao | Một chuỗi cho mỗi (activity, người) | Một chuỗi cho mỗi **người** (DEV làm cả PT và UT vẫn một chuỗi, không chồng nhau) |
| Task chưa giao | Một chuỗi cho mỗi (activity, kỹ năng yêu cầu) | **Không nối**, cảnh báo *chưa giao* (chuỗi theo kỹ năng coi như mỗi kỹ năng một người, sai sau khi đã phân công) |
| Thứ tự | WBS | Sẵn sàng sớm nhất hoặc WBS (mục 3), sửa tay được |

- Link logic tạo lại như Auto Link (tuỳ chọn, mặc định bật), dùng chung thông số đã lưu của Auto Link.
- Link Re-link tạo cũng là `isAuto()`: chạy Auto Link hoặc Re-link lại thì xoá và tạo lại. Link làm tay không bị đụng;
  link làm tay trùng với link cần tạo thì giữ (đếm là *đã có*).
- Task nhiều người (sửa tay): nằm trong chuỗi của từng người.
- Auto Link giữ nguyên hành vi cũ, dùng trước khi phân công.

## 3. Thứ tự task của mỗi người

### 3.1 Sẵn sàng sớm nhất (mặc định)

Mô phỏng nhanh để lấy **thứ tự**, không ghi ngày:

1. Thời lượng của task = Work / units của phân công, tính theo ngày làm việc của lịch dự án.
2. Task *sẵn sàng* khi mọi predecessor logic (Auto Link và làm tay, kể cả task ngoài phạm vi theo ngày hiện có) đã
   xong trong mô phỏng; bắt đầu = max(ngày sẵn sàng, ngày người đó rảnh, ngày người đó vào dự án).
3. Lặp: chọn task có ngày bắt đầu sớm nhất trong các task sẵn sàng, hoà thì theo thứ tự WBS; nối nó sau task trước
   của người đó.

Thứ tự lấy từ một lịch hợp lệ nên không tạo vòng. Ví dụ TESTER kiểm thử chức năng A (DEV chậm) và B (DEV nhanh): theo WBS
thì chờ A rồi mới tới B; theo sẵn sàng thì làm B trước, không bị ngồi chờ.

### 3.2 Theo WBS

Nối theo thứ tự WBS, dễ đoán, giống Auto Link. Mọi link đi từ trên xuống dưới WBS nên không tạo vòng (trừ link làm tay
ngược chiều).

### 3.3 Sửa tay (R2)

Thứ tự **không lưu riêng: chính là các link**. Mở lại hộp thoại thì thứ tự của mỗi người đọc từ chuỗi hiện có; task mới
(chưa trong chuỗi) chèn theo chế độ đang chọn. Nút *Sắp lại* tính lại thứ tự của người đang chọn (hoặc tất cả).
Đổi thứ tự bằng Lên/Xuống hoặc kéo thả. Thứ tự tạo vòng với link logic (ví dụ đặt KT trước PT của chính chức năng đó)
thì tô đỏ, không cho Apply.

## 4. Ngày vào dự án

ProjectLibre không tự dời task tới ngày resource bắt đầu available (auto-schedule-design mục 3.2), nên người TBD vào
dự án ngày 16/11 vẫn có thể được xếp làm từ ngày bắt đầu dự án.

- Task đầu tiên trong chuỗi của mỗi người nhận ràng buộc **Start No Earlier Than = ngày người đó bắt đầu available**
  (đầu khoảng Max Units > 0% đầu tiên), nếu ngày đó sau ngày bắt đầu dự án. Các task sau tự nằm sau nhờ chuỗi.
- Ràng buộc do Re-link đặt được đánh dấu (field task, lưu trong .pod): chạy lại thì gỡ và đặt lại. Task có ràng buộc
  người dùng tự đặt (khác ASAP, không có dấu) thì giữ nguyên, không ghi đè.
- Khoảng 0% ở giữa (nghỉ giữa chừng) và làm quá ngày rời dự án: không xử lý, đã có icon của `AvailabilityWarning` ở
  bảng task và cảnh báo trong kết quả (mục 6).

## 5. Dọn dữ liệu lịch cũ

- `levelingDelay` của phân công các task trong phạm vi đặt về 0 (kết quả Auto Schedule P2 cũ nếu có): lịch chỉ từ link
  và ràng buộc.
- Units của phân công giữ nguyên (đã lấy theo Max Units ở bước 2). Nếu sau khi tính lịch ngày bắt đầu rơi vào khoảng
  Max Units khác units của phân công thì chỉ cảnh báo.

## 6. Kết quả và cảnh báo

Sau Apply, ProjectLibre tính lịch, hộp thoại hiện:

| Mục | Nội dung |
|---|---|
| Ngày kết thúc | Project Finish so với deadline của Resource Plan (sau buffer): sớm/trễ bao nhiêu ngày làm việc |
| Người | Bắt đầu, kết thúc, số ngày chờ (ngày làm việc không có task giữa task đầu và task cuối), so với [Từ, Đến] |
| Chỗ nghẽn | Những người có task trên đường găng, xếp theo tổng thời lượng trên đường găng |
| Cảnh báo | Task chưa giao (không nối), người làm quá ngày Đến, vòng lặp (không Apply), units khác Max Units |

Trễ deadline thì gợi ý: quay lại bước 2 chuyển bớt việc của người nghẽn, hoặc bước 1 thêm người.

## 7. Hộp thoại *Re-link*

Ribbon HTD Planner, nhóm **Planning**, cạnh nút Assignment.

```
┌──────────────────────────────────────────────────────────────────────────────────────┐
│ Phạm vi: (•) Cả dự án ( ) Task đang chọn                                              │
│ Thứ tự:  (•) Sẵn sàng sớm nhất ( ) WBS     ☑ Nối hoạt động (GP→PT→KT)  ☑ Ngày vào dự án │
├──────────────────────────────────┬───────────────────────────────────────────────────┤
│ Người          Task  Bắt đầu  Kết thúc Chờ │ #  Task              Effort Bắt đầu  Kết thúc │
│ BA M #1 (TBD)   12   02/11    15/12    0  │ 1  GP Tạo hợp đồng    3.0  02/11    04/11   │
│ DEV M Nguyễn A  20   09/11    20/01    3  │ 2  GP Duyệt hợp đồng  2.0  05/11    06/11   │
│ TESTER J #1     18   16/11 ⚠  02/02    6  │ ...            [Lên] [Xuống] [Sắp lại]      │
├──────────────────────────────────┴───────────────────────────────────────────────────┤
│ Tạo 230 link · xoá 180 · giữ 40 · 5 task chưa giao ⚠                                    │
│ Kết thúc dự kiến 02/02/2027 · deadline 29/01/2027 · trễ 2 ngày                [Apply] [Đóng] │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

- Trước Apply: ngày lấy từ mô phỏng (mục 3.1), ghi *dự kiến*. Sau Apply: ngày thật của ProjectLibre.
- Chạy trong `JobProgressDialog` như Auto Link. Nhớ lựa chọn bằng `Preferences`.
- Cả lần Apply (link, ràng buộc, `levelingDelay`) là một bước Undo, tính lịch một lần ở cuối (như `AutoLinker`).

## 8. Lộ trình

| Giai đoạn | Nội dung |
|---|---|
| R1 | Lệnh Re-link: chuỗi theo người (sẵn sàng sớm nhất / WBS), không nối task chưa giao, ràng buộc ngày vào dự án, xoá `levelingDelay`, kiểm tra vòng, một Undo; hộp thoại thông số + số link + kết quả (ngày kết thúc so với deadline) |
| R2 | Bảng người / task của người, ngày dự kiến và thật, số ngày chờ, đổi thứ tự tay, *Sắp lại* |
| R3 | Chỗ nghẽn, cảnh báo quá ngày Đến; xuất Excel lịch theo người (`XlsxSheetWriter`) |

## 9. Đã chốt

1. Thứ tự mặc định: **sẵn sàng sớm nhất**; WBS là tuỳ chọn.
2. Ngày vào dự án: **ràng buộc Start No Earlier Than** trên task đầu chuỗi, có dấu `NormalTask.relinkConstraint`.
3. Task chưa giao: **không nối**, cảnh báo số task.

## 10. Ghi chú hiện thực (R1)

- `AutoLinkOptions.setRelink(true)`: `AutoLinkPlan` thay phần chuỗi bằng chuỗi theo người (`PersonSequencer`), tính
  thêm ràng buộc ngày vào và các task có `levelingDelay`. `AutoLinker` ghi chúng cùng transaction và bước Undo với link.
- Mô phỏng coi link tay tới task cha (summary) là link từ mọi task được nối bên dưới nó, nên thứ tự không tạo vòng với
  các link đó. Chế độ WBS: nếu task kế tiếp theo WBS của mọi người đều đang chờ link đi ngược WBS thì lấy task sẵn
  sàng sớm nhất, thay vì tạo vòng.
- Ước lượng trong hộp thoại dùng cùng mô phỏng; thử trên một dự án 631 task, 9 người: ước lượng trùng ngày ProjectLibre
  tính sau Apply, không ai làm hai task cùng lúc, không task nào trước ngày vào; sẵn sàng sớm nhất kết thúc sớm hơn WBS
  6 ngày làm việc.
- `calendar.compare()` của ProjectLibre đếm thừa một ngày làm việc với mốc có lẻ giây: số ngày so với deadline tính trên
  mốc làm tròn tới phút.
