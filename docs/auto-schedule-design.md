# Auto Schedule — Thiết kế

Tự động phân công và lập lịch WBS (đã import từ ULNL, đã có Auto Link) cho BA, DEV, TESTER, có hoặc không có deadline,
và tự tính thêm nguồn lực (Labor Category) khi thiếu.

## 1. Phạm vi và quyết định đã chốt

| # | Quyết định |
|---|---|
| 1 | Hiệu suất lấy từ bảng tra cứu NLTC (role × level → point, defaultPrice). Effort ULNL ứng với point 1.0 (100% unit) |
| 2 | Không tách task: mỗi task (một hoạt động của một chức năng) do đúng 1 người làm |
| 3 | Không tách KT: KT bắt đầu sau khi PT của chức năng xong (theo Auto Link) |
| 4 | Không xếp lịch PM/QA. Chỉ BA, DEV, TESTER |
| 5 | Các skill phát triển (BE, FE...) chạy song song sau khi xong GP |
| 6 | "Lane" đổi thành **Kỹ năng yêu cầu** (Required skill); có chức năng quản lý danh mục kỹ năng, xem mục 4 |
| 7 | 1 MM = 176 giờ (8 giờ × 22 ngày công) |

Ánh xạ hoạt động → role:

| Hoạt động ULNL | Role (positionName) | Kỹ năng yêu cầu |
|---|---|---|
| GP — Giải pháp | BA | không |
| PT — Phát triển | DEV | theo mã chức năng: BE, FE, Mobile... |
| KT — Kiểm thử | TESTER | không |

## 2. Ba loại = một bài toán

| Loại | Nguồn lực ban đầu | Được thêm người (TBD) | Deadline |
|---|---|---|---|
| 1 | Rỗng | Có | Bắt buộc |
| 2 | Resource có sẵn | Không | Không bắt buộc (có thì báo lệch) |
| 3 | Resource có sẵn | Có, chỉ phần thiếu | Bắt buộc |

Chung một engine (mục 6) và một wizard (mục 8).

## 3. Labor Category

### 3.1 Danh mục

Import từ CSV `id,level,point,defaultPrice,positionId,positionName` (bảng tra cứu NLTC).

- Lưu ở thư mục người dùng (không đưa vào repo vì có đơn giá), import lại được khi bảng thay đổi.
- Bỏ qua dòng `point = 0` (không làm được việc).
- Level 0 (point 0.3) không dùng khi tự thêm người, chỉ dùng được cho resource có sẵn.
- `defaultPrice` là đơn giá một man-month (VND/tháng).
- Tên level: 0 Intern, 1 Fresher (F), 2 Junior (J), 3 Junior+ (J+), 4 Middle (M), 5 Middle+ (M+), 6 Senior (S). Level, Tên level, Level viết tắt là 3 field riêng (trong danh mục, file `labor-categories.csv` cột `level, levelName, levelCode`, và trên resource) để export. File NLTC không có tên thì lấy tên mặc định. Ô chọn Level trên resource hiển thị gộp `4 - Middle (M)`, gõ số, tên hoặc viết tắt đều được.
- File hiện là ASCII, tên tiếng Việt đã thành `?` (G?TT...). Không ảnh hưởng BA/DEV/TESTER; parser đọc UTF-8, lỗi thì đọc theo
  bảng mã hệ thống.

### 3.2 Quy đổi effort

Effort ULNL là effort của người có point 1.0 (100% unit). Level có point gần 1.0 nhất là level chuẩn của role:

| Role | Level chuẩn | Point |
|---|---|---|
| BA | L2 | 1.03 |
| DEV | L2 | 1.00 |
| TESTER | L2 | 0.86 |

Chỉ dùng các field gốc của ProjectLibre để tính, không dùng field riêng:

- Work của task giữ nguyên = effort ULNL (Original Estimate). Task Fixed Work không bị đổi Work.
- Point quy ra **Max Units** của resource: đặt role và level cho resource thì Max Units = point (DEV L4 1.59 → 159%).
  Phân công mới lấy units = Max Units (tuỳ chọn có sẵn trong *Default Value Settings*).
- ProjectLibre tự tính thời lượng = Work / units.

```
units (Max Units)  = point × % phân bổ (mặc định 100%)
thời lượng (giờ)   = Work / units
man-month          = giờ / 176
```

Ví dụ task Fixed Work 80 giờ ULNL:

| Người làm | Point | Units | Work | Thời lượng |
|---|---|---|---|---|
| DEV L4 | 1.59 | 159% | 80 giờ | 50.3 giờ |
| DEV L2 | 1.00 | 100% | 80 giờ | 80 giờ |
| DEV L1 | 0.55 | 55% | 80 giờ | 145.5 giờ |
| TESTER L2 | 0.86 | 86% | 80 giờ | 93 giờ |

Đổi role/level sau khi đã phân công: Max Units đổi, nhưng units của các phân công cũ giữ nguyên (cần giao lại hoặc sửa units).
Người làm bán thời gian: sửa Max Units = point × % phân bổ (ví dụ L4 50% → 79.5%).

Resource có nhiều khoảng Available From (ví dụ 0% trước ngày vào dự án 2/11, 100% từ 2/11):

- Đặt/đổi role, level: nhân Max Units của **mọi** khoảng với point (0% giữ 0%, 100% → 159%, 50% → 79.5%).
  Đổi level thì quy từ point cũ sang point mới; role/level không có trong danh mục thì coi point = 1.
- Cột Max Units trên bảng resource của ProjectLibre chỉ hiện khoảng đang hiệu lực **hôm nay**.
- Phân công mới lấy Max Units của khoảng chứa **ngày bắt đầu task**; nếu khoảng đó 0% thì lấy khoảng đầu tiên sau đó có units > 0.
- ProjectLibre không tự dời task tới ngày resource bắt đầu available (không có resource leveling), và biểu đồ quá tải chỉ dùng Max Units hôm nay. Auto Schedule (P2, đã làm) tự xếp task trong các khoảng available.
- Resource được giao trong khoảng Max Units 0% (trước ngày vào dự án, sau ngày rời dự án): task giữ nguyên ngày, cột Indicators hiện icon cảnh báo (tam giác vàng), tooltip ghi tên resource và khoảng không available. Bỏ qua task đã hoàn thành hoặc đã cancel.

### 3.3 Field cần thêm

- Resource: role, level, point, đơn giá, danh sách skill, cờ TBD (người dự kiến tuyển, chưa có tên) — dùng field Generic có sẵn của ProjectLibre.
  Làm field riêng giống `activity` của `NormalTask`, lưu trong file dự án.
- Task: effort ULNL gốc (giờ, ở point 1.0) — dùng field Original Estimate đã có.

Tên người TBD: `DEV BE L3 #2 (TBD)`.

## 4. Kỹ năng (Skill)

### 4.1 Khái niệm

Tên hiển thị trên giao diện:

| Khái niệm | Tiếng Việt | Tiếng Anh |
|---|---|---|
| Kỹ năng task cần (trước đây "Lane") | Kỹ năng yêu cầu | Required skill |
| Kỹ năng của resource | Kỹ năng | Skills |
| Danh mục | Quản lý kỹ năng | Manage Skills |
| Bảng map từ ULNL | Mã chức năng và Kỹ năng yêu cầu | Function codes and required skills |

Trong tài liệu này "skill" dùng ngắn gọn cho cả hai.

- **Kỹ năng yêu cầu của task**: task PT cần người có skill nào. Lấy từ mã chức năng ở cột L của ULNL qua bảng
  map *mã chức năng → skill*.
- **Kỹ năng của resource**: những skill người đó làm được. Một người có thể có nhiều skill.
- Task chỉ được giao cho resource cùng role **và** có skill yêu cầu của task. Task không có skill yêu cầu thì ai cùng role
  cũng làm được.
- Các task cùng skill có thể chạy song song nếu đủ người. Các skill khác nhau luôn chạy song song.

### 4.2 Danh mục Skill

Hộp thoại *Quản lý kỹ năng* (menu cạnh Auto Link):

| Cột | Ví dụ |
|---|---|
| Mã | BE, FE, Mobile |
| Tên | Back-end, Front-end, Mobile |
| Role | DEV (để sau này dùng được cho role khác, ví dụ TESTER Automation) |
| Gồm các skill | Fullstack = BE + FE |

- Skill **cơ bản** (BE, FE, Mobile...) dùng được làm skill yêu cầu của task.
- Skill **tổ hợp** (Fullstack) chỉ gán cho resource, tương đương có đủ các skill thành phần.
- Thêm, sửa, xoá tuỳ ý. Danh mục dùng chung mọi dự án (thư mục người dùng). Mở dự án có skill chưa có trong danh mục thì
  tự thêm vào.
- Mặc định: BE (Back-end), FE (Front-end), Mobile (Mobile), Fullstack = BE + FE.

### 4.3 Thay đổi trong phần đã làm

Phần đã làm gọi khái niệm này là **Lane** (nghĩa là "làn": mỗi loại công việc phát triển một làn chạy song song, và trong
Auto Link các task cùng làn được nối thành chuỗi). Đổi thành Kỹ năng yêu cầu:

- Cột "Lane" của task → "Kỹ năng yêu cầu" / "Required skill".
- Hộp thoại *Mã chức năng và Lane* → *Mã chức năng và Kỹ năng yêu cầu*; danh sách chọn lấy từ danh mục kỹ năng (thay cho
  BE, FE, Mobile cố định trong code), chỉ cho chọn skill cơ bản.
- Chữ trong Auto Link ("mỗi Lane một chuỗi") → "mỗi kỹ năng yêu cầu một chuỗi".
- Tên field lưu trong file dự án giữ nguyên để mở được các file cũ; chỉ đổi tên hiển thị.

## 5. Phụ thuộc (đã làm ở P2)

Engine chỉ dùng **phụ thuộc logic**: các link giữa hoạt động của Auto Link (GP → PT BE, PT FE → KT) và link làm tay.

Link **chuỗi** (sequence) của Auto Link nối các task cùng hoạt động/skill thành một hàng, tức là coi như mỗi skill chỉ có
một người. Nếu giữ thì thêm người không rút ngắn được lịch. Trước khi xếp lịch, chạy lại Auto Link với
`linkSequences = false` (Auto Link tự xoá các link `isAuto()` nó đã tạo mà không tạo lại).

Task huỷ hoặc không có work được bỏ qua, như Auto Link.

Auto Link chạy lại với các phạm vi (dòng / chức năng) đã chọn lần trước trong hộp thoại Auto Link, luôn link giữa các
hoạt động. Ngoài link của Auto Link, engine dùng mọi link đang bật của dự án, đủ 4 loại FS/SS/FF/SF và lag. Link tới/từ
task cha (summary) đi qua 2 điểm của task cha: điểm bắt đầu (các task con bắt đầu sau) và điểm kết thúc (từ task con đầu
tiên tới task con cuối cùng), như ProjectLibre tính. Link vòng lặp: báo lỗi, không ghi gì.

## 6. Engine lập lịch (đã làm ở P2)

Java thuần, không phụ thuộc UI, có unit test. Package `com.projectlibre1.pm.scheduling.auto` (projectlibre_core):

| Lớp | Vai trò |
|---|---|
| `AutoScheduler` | Engine: CPM lấy LS, list scheduling |
| `AutoTask`, `AutoResource`, `WorkingTime` | Đầu vào/kết quả của engine, lịch làm việc |
| `AutoScheduleModel`, `AutoScheduleOptions` | Đọc dự án thành task/resource cho engine; ngày bắt đầu, giữ/giao lại, ánh xạ hoạt động → role |
| `AutoScheduleJob` | Chạy Auto Link → engine → ghi vào dự án, từng bước cho hộp thoại tiến trình, một lần Undo |
| `AutoScheduleWarning` | Cảnh báo task không tìm được người |

Phân loại task lá:

| Loại | Điều kiện | Xử lý |
|---|---|---|
| Xếp lịch | Có hoạt động ánh xạ ra role, không huỷ, có work, chưa bắt đầu | Chọn resource, tính ngày |
| Giữ ngày | Đã bắt đầu (Actual Start) hoặc đã xong | Giữ ngày; resource được giao bận trong khoảng đó |
| Bị động | Còn lại (PM/QA, huỷ, không work, mốc...) | Giữ thời lượng, ràng buộc và Leveling Delay người dùng đặt, chạy theo link |

Ràng buộc Start No Earlier Than / Must Start On của task được tôn trọng (ngày sớm nhất).

**Đầu vào:** task lá có hoạt động (role, skill yêu cầu, effort ULNL), phụ thuộc logic, nhóm resource
(role, kỹ năng, Max Units, khoảng thời gian có thể tham gia (Available From/To), có sẵn hay TBD (Generic)), lịch dự án, ngày bắt đầu.

**Thuật toán (list scheduling):**

1. Hàng đợi các task đã đủ điều kiện (mọi task trước đã xếp), sắp theo **thứ tự WBS**: chức năng đầu WBS được giao
   người trước, mỗi chức năng làm liền GP → PT → KT.
2. Lấy task, với mỗi resource hợp lệ (đúng role, có skill, trong khoảng tham gia):
   `bắt đầu = max(ngày sẵn sàng của task, ngày rảnh của resource)`, `kết thúc = bắt đầu + Work / Max Units`.
   Chọn resource có **ngày kết thúc sớm nhất** (tự ưu tiên người point cao).
   Bằng nhau thì ưu tiên người ít skill hơn, để dành người Fullstack cho chỗ thiếu.
3. Cập nhật ngày rảnh của resource, đưa các task sau đã đủ điều kiện vào hàng đợi.

Ban đầu (bản đầu của P2) hàng đợi sắp theo LS của CPM không giới hạn nguồn lực (task găng trước). Thử trên WBS thật
(DTMS, 3561 task, 1 dev BE, 1 dev FE) thì các chức năng ngắn bị coi là không gấp và bị xếp xen kẽ: chức năng 2,4 MD
"Bộ lọc thời gian" có GP 3/2027, PT 1/2028, KT 3/2028. Đã đổi sang thứ tự WBS (chốt với anh Dũng); thứ tự theo LS vẫn còn
là tuỳ chọn `AutoScheduleOptions.setCriticalFirst()` (chưa có trên giao diện).

O(n log n × số resource mỗi role). Tính theo ngày làm việc của lịch dự án (link, task không người) và lịch của
resource (thời lượng).

Chi tiết đã làm:

- "Ngày rảnh" của resource là danh sách khoảng bận đã gộp: task được chèn vào khoảng trống giữa các task trước của
  resource nếu đủ chỗ, vì thứ tự xếp theo WBS chứ không theo thời gian.
- Work dùng để tính là Work sau khi giao người: task effort driven có Work bằng Original Estimate thì lấy đúng Original
  Estimate (ProjectLibre đưa Work về Original Estimate khi thêm/bớt resource). Thời lượng cắt tới mili-giây như
  ProjectLibre; lệch vài mili-giây có thể đẩy task sau qua giờ nghỉ trưa / sang ngày hôm sau.
- Trong khoảng tham gia: task bắt đầu trong khoảng Max Units > 0, units = Max Units của khoảng chứa ngày bắt đầu, và
  không được chạy qua khoảng 0% (ví dụ nghỉ giữa dự án); nếu chạy qua thì bắt đầu ở khoảng có units tiếp theo.
- Không resource nào hợp lệ (thiếu role/kỹ năng, hoặc không còn khoảng available): task giữ thời lượng, bắt đầu khi đủ
  điều kiện, cột Indicators có icon cảnh báo (tooltip ghi role và kỹ năng thiếu). Icon mất khi task được giao resource
  đúng role và kỹ năng, hoặc khi chạy lại tìm được người.
- Thử với 3000 task, 16 resource: khoảng 1 giây cả Auto Link, xếp lịch, ghi và tính lại lịch. WBS DTMS (5450 dòng,
  3561 task được xếp): khoảng 1,1 giây.

**Task đã giao sẵn:** tuỳ chọn *giữ phân công hiện có* (chỉ tính lại ngày) hoặc *giao lại tất cả*. Giữ phân công: task
giữ resource và thời lượng hiện có, bắt đầu khi mọi resource của nó available và rảnh.

**Ghi kết quả vào dự án:** tạo assignment với units = Max Units. Thứ tự công việc của mỗi resource được ghi bằng
**link FS nối các task của cùng một người** theo thứ tự ngày bắt đầu đã tính (link của Auto Link, `isAuto()`; chạy lại
Auto Schedule hoặc Auto Link thì được xoá và tạo lại). Mọi task as soon as possible, Leveling Delay = 0: nhìn link là
biết task chờ task nào, muốn đổi thứ tự thì sửa link.

- Task đã bắt đầu có thể là task trước của link, không là task sau.
- Resource chưa available (Max Units 0%, ví dụ trước ngày vào dự án) trong thời gian task chạy: task được đặt ràng buộc
  Start No Earlier Than ngày đã tính. Auto Schedule ghi nhớ ràng buộc mình đặt (`NormalTask.autoScheduleStart`); chạy
  lại thì tính lại ràng buộc đó, ràng buộc người dùng đã sửa thì giữ.
- Sau khi ghi, ProjectLibre tính lại lịch; task bắt đầu muộn hơn ngày đã tính được đếm để kiểm tra (DTMS: 0).

Các cách đã thử và bỏ:

| Cách | Kết quả |
|---|---|
| `levelingDelay` của assignment | Task vẫn bắt đầu theo link (cột Start sai), chỉ assignment bị lùi |
| `levelingDelay` của task | Đúng ngày, nhưng con số delay (ví dụ 1825 giờ) không cho biết task chờ ai, khó kiểm tra và sửa; anh Dũng không đồng ý tự động gán delay |
| Start No Earlier Than cho mọi task | Ghi đè ràng buộc người dùng, cũng không cho biết lý do |

Task Fixed Work giữ nguyên Work: ProjectLibre làm tròn thời lượng tới mili-giây nên Work có thể lệch dưới 1 giây
(80 giờ → 79,99997 giờ, như khi tự giao resource có units 159%).

Cả thao tác ghi (link, assignment, ràng buộc, cảnh báo) là một lần Undo.

**Kết quả trên WBS DTMS** (1 dev BE, 1 dev FE, 3 BA, 2 tester TBD, bắt đầu 01/10/26): xong 28/02/28 (bản theo LS và
Leveling Delay: 06/06/28). Giới hạn dưới do dev BE: 737 MD / 203% ≈ 363 ngày công. Các chức năng đầu WBS làm liền trong
1–3 ngày; các chức năng cuối WBS vẫn trải dài (median 133 ngày) vì BA làm xong mọi GP trước 3/2027 còn PT phải xếp hàng
chờ 1 dev mỗi kỹ năng đến 2028.

**Giao diện tạm (P2):** nút *Tự động xếp lịch* (nhóm *Lập lịch* trên tab HTD - VTIT), hộp thoại chọn ngày bắt đầu
(mặc định ngày bắt đầu dự án; không sớm hơn ngày đó) và giữ/giao lại phân công, xem trước số task và số resource có role.

## 7. Tính nguồn lực theo deadline (Loại 1 và 3)

Deadline hiệu lực = deadline − buffer (ngày, cấu hình).

1. **Kiểm tra khả thi:** CPM với level cao nhất được phép. Vì không tách task, task dài nhất trên đường găng là giới hạn dưới.
   Nếu vẫn vượt deadline → báo không khả thi, ngày sớm nhất đạt được và các task gây ra
   (gợi ý: tách chức năng trong ULNL, nâng level, giảm scope).
2. **Khởi tạo:** resource có sẵn (khoá) + với mỗi role/skill, nếu cần thì thêm TBD level mặc định cho tới
   `ceil(Work của role/skill / (Max Units × giờ làm việc trong khoảng hoạt động của role/skill))`.
3. **Lặp:** chạy engine. Nếu trễ, tìm **chỗ nghẽn** = role/skill mà các task găng phải chờ người lâu nhất.
   Thêm 1 TBD có role và skill đó. Dừng khi kịp deadline, chạm số người tối đa, hoặc thêm người không còn rút ngắn được.
4. **Rút gọn:** thử bỏ từng TBD (thêm sau bỏ trước), còn kịp deadline thì bỏ hẳn.

Người TBD thêm vào chỉ có một skill cơ bản. Tuỳ chọn *cho phép thêm Fullstack*: khi BE và FE cùng thiếu nhưng mỗi bên
không đủ việc cho một người, thử thêm một người Fullstack.

Mục tiêu (chọn trong wizard):
- **Ít người nhất:** luôn thêm level mặc định của role.
- **Chi phí thấp nhất:** ở bước 3, thử mọi level được phép, chọn level có (số ngày rút ngắn / đơn giá) lớn nhất.

Nếu chưa kịp và nguyên nhân là một task dài do TBD làm, đề xuất nâng level người đó (trong giới hạn cho phép).

## 8. Wizard "Auto Schedule"

1. **Mục tiêu:** ngày bắt đầu, deadline (tuỳ chọn), buffer,
   chế độ *chỉ dùng resource có sẵn* / *tự bổ sung để kịp deadline*, mục tiêu *ít người* / *chi phí thấp*.
2. **Role và Skill:** ánh xạ hoạt động → role (mặc định như mục 1); kiểm tra task PT chưa có skill (cảnh báo, mở bảng map
   mã chức năng → skill).
3. **Labor Category:** xem bảng đã import, nút import CSV.
4. **Nguồn lực:**
   - Bảng resource trong dự án: role, level, kỹ năng, Max Units, từ ngày, đến ngày.
   - Bảng được phép thêm: role/skill, level mặc định, level tối thiểu–tối đa, số người tối đa, cho phép Fullstack.
   - Tuỳ chọn giữ phân công hiện có.
5. **Kết quả:** ngày kết thúc so với deadline, danh sách người thêm, tải theo tuần mỗi role/skill, cảnh báo.
   Back để chỉnh, Apply để ghi vào dự án, Xuất báo cáo.

Chạy trong `JobProgressDialog` như import ULNL. Lưu các lựa chọn bằng `Preferences` như `AutoLinkDialog`.

## 9. Báo cáo nguồn lực (Excel, dùng `XlsxSheetWriter`)

1. **Nhân sự:** người, role, level, skill, có sẵn/TBD, ngày vào, ngày ra, số ngày làm việc, số ngày rảnh xen giữa.
2. **Tải theo tuần (và tháng):** man-day theo role/skill/level, % sử dụng.
3. **Effort và chi phí:** Work (= effort ULNL), thời lượng, man-month (176 giờ);
   chi phí theo effort = MM × đơn giá; chi phí theo thời gian chiếm dụng = số tháng từ ngày vào tới ngày ra × đơn giá.
4. **Tiến độ và rủi ro:** ngày kết thúc, so với deadline, task găng, chỗ nghẽn, task dài nhất mỗi role.

## 10. Lộ trình

| Giai đoạn | Nội dung | Kết quả |
|---|---|---|
| P1 (đã làm) | Labor Category (import CSV, quy đổi effort), danh mục kỹ năng, đổi Lane → Kỹ năng yêu cầu, field resource (role, level, kỹ năng; TBD dùng cờ Generic có sẵn) | Gán resource có role/level/kỹ năng và tính đúng work |
| P2 (đã làm) | Engine (thứ tự WBS) + ghi vào dự án (link FS theo resource, SNET khi resource chưa available), Auto Link không chuỗi, hộp thoại tạm | Loại 2 chạy được (tạm thời chưa có wizard đầy đủ) |
| P3 | Tính nguồn lực theo deadline | Loại 1, 3 |
| P4 | Wizard | Giao diện hoàn chỉnh |
| P5 | Báo cáo Excel | Báo cáo nguồn lực |
