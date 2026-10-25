# Assignment — Thiết kế (bước 2 của 3)

| Bước | Chức năng | Kết quả |
|---|---|---|
| 1 | Resource Plan (docs/resource-plan-design.md) | Danh sách người theo role, level, kỹ năng, khoảng thời gian tham gia |
| 2 | **Assignment** (tài liệu này) | Mỗi task hoạt động có đúng một người làm |
| 3 | Re-link | Sinh lại chuỗi predecessor theo từng người, ProjectLibre tự tính lịch |

**Bước này chỉ quyết định *ai làm task nào*, không xếp ngày.** Auto-assign của Auto Schedule sai vì vừa chọn người vừa
xếp ngày (`levelingDelay`). Ở đây máy chỉ gợi ý, người duyệt. Thứ tự và ngày làm việc của từng người là việc của bước 3.

## 1. Phạm vi

- Giao các task hoạt động của ULNL (GP, PT, KT; sau này UT) cho resource có role: người có sẵn hoặc TBD do Resource Plan
  tạo.
- **Một task = một người**, giống giả định của Resource Plan (mục 4.2). Không tách một task cho nhiều người. Chức năng
  quá lớn thì tách trong ULNL.
- Task đã hoàn thành, đã cancel, task cha: bỏ qua, không hiện.
- Ngoài phạm vi: thứ tự task của mỗi người, ngày bắt đầu (bước 3), tải theo tuần (biểu đồ resource của ProjectLibre sau
  bước 3), task Level of Effort cho PM/QA.

## 2. Dữ liệu dùng lại (đã có)

| Dữ liệu | Nguồn |
|---|---|
| Role của task | Activity của task → role (`ResourcePlan.role()`: GP → BA, PT → DEV, KT → TESTER) |
| Kỹ năng yêu cầu | Kỹ năng yêu cầu (Lane) của task, chỉ với DEV |
| Chức năng, cụm | Task cha của activity = chức năng; cha của chức năng = **cụm** (module, hoặc cấp WBS thấp hơn nếu người dùng đã chia module lớn) |
| Effort | Work của task, giờ ở point 1.0 |
| Người | Resource labor có role: role, level, kỹ năng, Generic (TBD) hay có sẵn, các khoảng Max Units |
| Làm được kỹ năng không | `SkillCatalog.canDo()` (Fullstack làm được BE và FE) |
| Hệ số sử dụng, ngưỡng thiếu/thừa | Thông số của Resource Plan (`ResourcePlanOptions`) |

## 3. Tải của một người

Cùng công thức với Resource Plan, nên con số khớp giữa hai bước:

```
năng lực (giờ quy đổi point 1.0) = năng lực 1 người của dòng Resource Plan chứa người đó
                                 = point × % phân bổ × giờ làm việc trong [Từ, Đến] ∩ [bắt đầu, deadline] × hệ số sử dụng
đã giao                          = Σ Work các task giao cho người đó (task nhiều người thì chia đều)
tải                              = đã giao / năng lực
```

- Đọc người bằng chính `ResourcePlan` (thông số đã lưu trong dự án), nên năng lực luôn bằng con số của hộp thoại
  Resource Plan. `point × %` là Max Units của khoảng availability (memory: chỉ dùng field gốc).
- Giờ làm việc theo lịch dự án (bỏ ngày nghỉ, lễ). Người không có khoảng nào > 0% thì năng lực 0.
- Người có sẵn mà availability mở (từ trước / mãi mãi): khoảng tính từ ngày bắt đầu tới deadline của Resource Plan,
  nếu chưa có deadline thì tới Project Finish.
- Màu theo ngưỡng của Resource Plan (mục 4.3): trong ngưỡng không tô; vượt ngưỡng cảnh báo màu cam; vượt ngưỡng không đạt
  màu đỏ. Tải thấp hơn ngưỡng thừa chỉ hiện số, không tô, vì đang giao dở thì ai cũng thừa.
- Đây là tải trên **cả khoảng** của người đó. Đủ năng lực chưa chắc kịp theo ngày; lịch thật có sau bước 3.

## 4. Hộp thoại *Assignment*

Ribbon HTD Planner, nhóm **Planning**, cạnh nút Resource Plan.

```
┌──────────────────────────────────────────────┬───────────────────────────────────────┐
│ Lọc: [Role ▾] [Kỹ năng ▾] [Module ▾]          │ Người: [Role ▾]                       │
│ ☐ Chỉ task chưa giao  ☐ Chỉ task có cảnh báo  │                                       │
├──────────────────────────────────────────────┤ BA M #1 (TBD)   ████████░░  82%  2 module│
│ ▾ Module Quản lý hợp đồng                     │ BA J #1 (TBD)   ██████████ 104%  3 module│
│   ▾ Tạo hợp đồng                              │ DEV M Nguyễn A  ██████░░░░  61%  BE     │
│       GP  BA   3.0 MD  BA M #1     🔒          │ DEV J #2 (TBD)  ███░░░░░░░  30%  FE     │
│       PT  DEV BE 8.0 MD  DEV M Nguyễn A        │ TESTER J #1     ░░░░░░░░░░   0%        │
│       KT  TESTER 2.5 MD  (đề xuất) TESTER J #1 │                                       │
│   ▸ Duyệt hợp đồng          ⚠                 │                                       │
├──────────────────────────────────────────────┴───────────────────────────────────────┤
│ [Giao cho người đã chọn] [Giao cụm...] [Bỏ giao] [Gợi ý] [Chấp nhận đề xuất] [Khoá]    │
│ Đã giao 142/180 task · 38 chưa giao · 5 cảnh báo           [Xuất Excel] [Apply] [Đóng] │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

### 4.1 Bên trái: task

- Cây theo đúng WBS của dự án, theo thứ tự WBS, bao nhiêu cấp cũng được (module → nhóm con → chức năng → task hoạt
  động). Module lớn thì người dùng tự chia thành các cấp WBS thấp hơn rồi giao từng phần; hộp thoại không giới hạn số MD
  của một module cho một người.
- Cột: activity, role, kỹ năng, effort (MD, đổi được MH/MD như Resource Plan), người được giao, người đề xuất, khoá,
  cảnh báo.
- Dòng chức năng và module hiện tóm tắt: số task chưa giao, các người đang làm.
- Lọc: role, kỹ năng, module, chỉ task chưa giao, chỉ task có cảnh báo.
- Chọn nhiều dòng; chọn một dòng WBS cha (ở bất kỳ cấp nào) là chọn tất cả task con. Riêng khi giao cho một người, chỉ
  các task con hợp với người đó được giao (mục 4.3).

### 4.2 Bên phải: người

- Mỗi resource labor có role một dòng: tên, role, level, kỹ năng, khoảng [Từ, Đến], thanh tải, số task, số MD đã giao.
- Lọc theo role; khi bên trái đang chọn task, người không đúng role hoặc không đủ kỹ năng bị làm mờ (vẫn chọn được). Với
  dòng tổng hợp đang chọn, người chỉ bị làm mờ khi không làm được task nào bên dưới.
- Chọn một người thì bên trái tô các task của người đó.

### 4.3 Thao tác

| Thao tác | Kết quả |
|---|---|
| **Giao cho người đã chọn** (hoặc nhấp đúp vào người) | Các task đang chọn giao cho người đó, thay người cũ. Task chọn trực tiếp mà sai role/kỹ năng thì hỏi trước khi giao. Dưới một dòng tổng hợp đang chọn thì **chỉ giao các task đúng role và kỹ năng của người đó** (`canDo()`, theo bộ lọc), các task khác giữ người cũ; dòng trạng thái ghi số task được giữ. Không có task nào hợp thì báo, không đổi gì |
| **Giao cụm...** | Với các dòng WBS đang chọn (module, nhóm con, chức năng): chọn 1 người cho mỗi role có task (BA, DEV theo từng kỹ năng, TESTER). Mỗi task về người cùng role/kỹ năng. Role bỏ trống thì không đổi |
| **Bỏ giao** | Xoá người được giao của các task đang chọn |
| **Gợi ý** | Điền cột *Đề xuất* cho task chưa giao và chưa khoá (mục 5) |
| **Chấp nhận đề xuất** | Đề xuất của các task đang chọn (hoặc tất cả nếu không chọn) thành người được giao |
| **Khoá / Mở khoá** | Task khoá không bị Gợi ý, Giao, Giao cụm, Bỏ giao, Thay người đụng tới; dòng trạng thái ghi số task khoá được giữ. Muốn đổi người thì mở khoá trước. Bấm vào ô khoá (cột đầu) để khoá/mở một task, hoặc các task dưới một dòng tổng hợp (theo bộ lọc) |
| Kéo task thả vào người | Như *Giao cho người đã chọn* (kéo một dòng tổng hợp thì chỉ các task hợp với người được thả vào) |

Ví dụ: chọn module *Quản lý hợp đồng* rồi giao cho *DEV M Nguyễn A* (BE) thì chỉ các task PT cần BE (hoặc không cần kỹ năng)
về Nguyễn A; GP, KT và PT FE giữ nguyên. Muốn giao mỗi role một người cho cả module thì dùng *Giao cụm...*. Một task được
coi là "dưới dòng tổng hợp đang chọn" khi có một dòng cha (ở bất kỳ cấp nào) đang được chọn, kể cả khi chính task đó cũng
được chọn (ví dụ chọn bằng Shift từ module xuống).

Mọi thao tác chỉ sửa trong hộp thoại, thanh tải tính lại ngay. **Apply** mới ghi vào dự án (mục 7).

## 5. Gợi ý

**Gợi ý chỉ là gợi ý:** nó điền cột *Đề xuất*, không bao giờ tự thay phân công. Người dùng quyết định chấp nhận, sửa hay
bỏ qua.

Gợi ý theo **cụm chức năng**, để một người làm trọn một chức năng (và nếu được, trọn cụm) của role mình. Ngưỡng dùng
**chung với Resource Plan** (mục 4.3), không có ngưỡng riêng cho từng người. "Còn trong ngưỡng" nghĩa là sau khi thêm
task, tải của người đó không vượt ngưỡng cảnh báo thiếu.

1. Duyệt các chức năng (cha trực tiếp của task hoạt động; task không có cha là một chức năng riêng) theo thứ tự WBS.
   Các task chưa giao, chưa khoá cùng role/kỹ năng trong một chức năng là **một nhóm**, giao cho cùng một người:
2. **Ứng viên:** resource đúng role, `canDo(kỹ năng của người, kỹ năng yêu cầu)`, có năng lực > 0.
3. **Chọn** theo thứ tự ưu tiên ("tải sau khi thêm" tính cả các đề xuất trước đó trong cùng lần Gợi ý):
   1. người đã làm task cùng role trong **chức năng này** (đã giao, đã khoá, hoặc vừa được đề xuất), nếu còn trong ngưỡng;
   2. người đã làm task cùng role trong **cụm này** (cha của chức năng), nếu còn trong ngưỡng;
   3. **người có sẵn** còn trong ngưỡng (còn rảnh), tải sau khi thêm thấp nhất;
   4. **người TBD** có tải sau khi thêm thấp nhất (nếu không có TBD thì người có tải sau khi thêm thấp nhất).

   Hoà thì ưu tiên người có kỹ năng khớp đúng (BE) trước người tổ hợp (Fullstack), rồi thứ tự trong bảng người.
4. Không có ứng viên: để trống, cảnh báo *không có người đúng role/kỹ năng*.
5. Mọi người đều vượt ngưỡng không đạt: vẫn đề xuất người tải thấp nhất, cảnh báo đỏ (Resource Plan thiếu người).

Cụm là cha trực tiếp của chức năng, nên khi người dùng đã chia module lớn thành các cấp WBS thấp hơn thì gợi ý giữ theo
phần đã chia, không dồn cả module cho một người.

Tham số (hỏi mỗi lần bấm *Gợi ý...*, nhớ bằng `Preferences`, có nút *Mặc định*): giữ cụm chức năng (bật; tắt thì mỗi
task một nhóm, bỏ bước 3.1), giữ cụm (bật; tắt thì bỏ bước 3.2), ưu tiên người có sẵn (bật; tắt thì bỏ bước 3.3 và 3.4,
lấy người có tải sau khi thêm thấp nhất).

Hiển thị: cột *Đề xuất* (chữ nghiêng xanh lá; dòng tổng hợp ghi số đề xuất bên dưới), thanh tải có phần xanh lá nhạt là
effort được đề xuất và ghi `tải hiện tại → tải khi chấp nhận`.

Gợi ý không tính ngày, nên có thể đề xuất một người làm nhiều chức năng mà bước 3 xếp ra trễ. Lúc đó quay lại hộp thoại
để chuyển bớt việc (mục 6).

## 6. Thao tác hàng loạt (A3)

- **Thay người...:** chuyển tất cả task chưa khoá của người A (người đang chọn bên phải) sang người B cùng role. Tuỳ
  chọn *Cả các task đã khoá* chuyển luôn task khoá (vẫn giữ khoá). Task nhiều người thì chỉ thay A, giữ người kia; đề
  xuất cho A cũng chuyển sang B. Dùng khi đã tuyển được người thật cho một chỗ TBD. Sau đó resource TBD không còn phân
  công, lần Apply Resource Plan sau sẽ tự xoá (Resource Plan mục 5).
- **Chuyển bớt việc...:** với người đang chọn (không chọn ai thì mọi người vượt ngưỡng cảnh báo thiếu), lấy task từ
  cuối WBS theo cụm chức năng: các task cùng role/kỹ năng của một chức năng về cùng một người, là người cùng
  role/kỹ năng có tải thấp nhất mà sau khi nhận vẫn trong ngưỡng; chức năng không ai nhận trọn thì chuyển từng task.
  Dừng khi người đó về trong ngưỡng. Chỉ chuyển task của riêng người đó và chưa khoá. Hiện trước danh sách sẽ chuyển
  (task, chức năng, effort, từ, sang) và tải trước → sau của từng người, người dùng xác nhận. Không ai còn chỗ thì báo
  (cần thêm người ở Resource Plan).
- **Xuất Excel...** (`XlsxSheetWriter`, ghi được nhiều sheet): xuất đúng như hộp thoại đang hiện, kể cả thay đổi
  chưa Apply, effort theo đơn vị đang chọn. Sheet *Phân công*: ID, module (cấp đầu WBS), chức năng, task, activity,
  role, kỹ năng, effort, người làm, đề xuất, khoá, cảnh báo. Sheet *Tải*: người, TBD, role, kỹ năng, level, từ, đến,
  Max Units, năng lực, đã giao, số task, tải, cảnh báo. Số ghi dạng số, có filter.

## 7. Ghi vào dự án

**Phân công gốc của ProjectLibre là nơi lưu duy nhất:**

- Apply: với mỗi task đổi người, xoá phân công cũ, tạo phân công mới. Units = Max Units của người, theo quy tắc đã có
  (khoảng chứa ngày bắt đầu task; nếu 0% thì khoảng đầu tiên sau đó có units > 0). Work giữ nguyên, ProjectLibre tự
  tính duration = Work / units. Không dùng `levelingDelay`, không đổi ngày, không đổi link.
- Cả lần Apply là một bước Undo.
- Task có nhiều hơn một phân công (sửa tay ngoài hộp thoại): hiện tất cả người, cảnh báo; giao lại thì thay bằng một người.
- Phân công tới resource không có role (resource cũ, không phải labor): giữ nguyên, hiện tên, không tính tải.
- **Khoá** là một field của task (`NormalTask.assignmentLocked`), lưu trong file .pod; ghi khi Apply, cùng bước Undo
  với phân công. Tham số Gợi ý nhớ bằng `Preferences` (dùng chung mọi dự án). Cột *Đề xuất* không lưu: đóng hộp thoại
  là mất, bấm Gợi ý lại để có.
- Mở lại hộp thoại: đọc phân công hiện có của dự án.

## 8. Cảnh báo

Dạng icon (không popup), trong hộp thoại và ở cột Indicators của bảng task (cạnh icon *resource không available* của
`AvailabilityWarning`):

| Cảnh báo | Khi nào | Ở đâu |
|---|---|---|
| Chưa giao | Task hoạt động không có phân công | Hộp thoại |
| Sai role | Role của người khác role của activity | Hộp thoại + bảng task |
| Thiếu kỹ năng | `canDo()` sai | Hộp thoại + bảng task |
| Nhiều người | Task có hơn một phân công | Hộp thoại + bảng task |
| Quá tải | Tải của người vượt ngưỡng (cam/đỏ) | Bên phải hộp thoại |
| Không có năng lực | Người không có khoảng nào Max Units > 0 | Bên phải hộp thoại |

Tooltip ghi lý do cụ thể (ví dụ: *cần BE, DEV J #2 chỉ có FE*). Ở bảng task, icon riêng (người + tam giác cam) chỉ hiện
với task hoạt động chưa hoàn thành, chưa cancel; tính lại mỗi lần vẽ từ phân công hiện có, không cần mở hộp thoại.

## 9. Lộ trình

| Giai đoạn | Nội dung |
|---|---|
| A1 | Hộp thoại: bảng task + bảng người, tải, giao tay hàng loạt, Giao cụm, Bỏ giao, cảnh báo, Apply (một Undo), đọc lại từ dự án |
| A2 | Gợi ý + Chấp nhận đề xuất, khoá task (lưu trong dự án), tham số Gợi ý, kéo-thả task sang người |
| A3 | Thay người, Chuyển bớt việc, Xuất Excel; icon Sai role / Thiếu kỹ năng / Nhiều người ở bảng task |

Sau đó: bước 3 Re-link — mặc định nối task của mỗi người theo thứ tự WBS, đổi thứ tự ở bước đó.

## 10. Đã chốt

1. **Ngưỡng:** dùng chung ngưỡng thiếu/thừa của Resource Plan.
2. **Module lớn:** người dùng tự đánh giá, chia thành các cấp WBS thấp hơn rồi giao từng phần. Không giới hạn MD/module/người.
3. **Người có sẵn trước TBD**, khi người có sẵn còn rảnh (còn trong ngưỡng). Đây chỉ là gợi ý; người dùng quyết định phân
   công.
