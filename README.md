# ProjectLibre (bản tùy biến)

Phần mềm quản lý dự án trên desktop, viết bằng Java Swing, mã nguồn mở, thay thế cho Microsoft Project. Repo này dựa trên **ProjectLibre 1.9.8** và bổ sung một số tính năng phục vụ công việc lập kế hoạch nội bộ.

- Phiên bản hiện tại: **1.9.9** (khai báo trong [build.properties](projectlibre_build/build.properties))
- Main class: `com.projectlibre1.main.Main` ([Main.java](projectlibre_ui/src/com/projectlibre1/main/Main.java))
- Đọc/ghi file `.pod` (định dạng gốc), import/export MS Project (`.mpp`, `.xml`) qua MPXJ

## Tính năng bổ sung so với ProjectLibre gốc

- **WBS ↔ Excel**: xuất và nhập WBS dạng file Excel; nhập WBS từ workbook ước lượng ULNL, kèm mã chức năng và bảng map mã chức năng → Lane (BE, FE, Mobile…)
- **Original Estimate**: estimate gốc từ ULNL, tách khỏi Work; Work tự về Original Estimate khi thêm/bớt resource. Nhóm *Estimate* trên tab Task để ẩn/hiện cột, đặt Original Estimate theo Work, khôi phục Work theo Original Estimate
- **Cancel task** (nhóm *Status* trên tab Task và context menu): task bị gạch ngang, Work còn lại về 0 nhưng giữ link/phân công, un-cancel để khôi phục; cột lý do và ngày cancel; filter ẩn/chỉ hiện task đã cancel
- **Lọc tên task** mà vẫn giữ các task cha (summary) để thấy ngữ cảnh
- **Link/Unlink nhiều task** trong một transaction, có hộp thoại tiến trình; có trên context menu của bảng task
- **Phím tắt kiểu MS Project** cho indent, outdent, link, unlink
- **Ribbon Task**: mở/thu outline từng cấp mỗi lần bấm; hộp thoại *Default Value Settings* — task mới mặc định là *Fixed Work*
- **Assignment**: units mặc định lấy theo *Max Units* của resource; giữ nguyên work với task Fixed Work
- **Bảng tính**: dán vào mọi ô đang chọn; click một lần để chọn, double click để sửa
- Sửa lỗi cuộn chuột trong Gantt và split view
- Bản dịch tiếng Việt ([menu_vi.properties](projectlibre_ui/src/com/projectlibre1/menu/menu_vi.properties))

## Cấu trúc thư mục

| Thư mục | Nội dung |
|---|---|
| `projectlibre_core` | Đối tượng nghiệp vụ, thuật toán lập lịch, preferences, chuỗi giao diện (`com/projectlibre1/strings/client*.properties`) |
| `projectlibre_ui` | Giao diện Swing: view, dialog, menu, toolbar (`com/projectlibre1/menu/menu*.properties`) |
| `projectlibre_reports` | Report view dùng JasperReports |
| `projectlibre_exchange` | Import/export (MS Project, Excel…) |
| `projectlibre_contrib` | Thư viện bên thứ ba (`lib/`), được ProGuard gộp thành `projectlibre-contrib.jar`, `projectlibre-script.jar`, `projectlibre-reports.jar` |
| `projectlibre_build` | Script Ant build và đóng gói, tài nguyên cài đặt (`resources/`), license, file mẫu (`resources/samples/`) |

## Môi trường phát triển

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| JDK | **21** | Mã được biên dịch với `source/target = 21`. Đã kiểm tra với Eclipse Temurin 21 |
| Apache Ant | 1.10.x | Công cụ build chính |
| IDE (tùy chọn) | IntelliJ IDEA hoặc Eclipse | Repo có sẵn `.idea/`, `projectlibre_desktop.iml` và file `.project` của Eclipse |
| Git | bất kỳ | |

Các thư viện phụ thuộc (ProGuard, ant-contrib, MPXJ, POI, JasperReports, Groovy…) **đã có sẵn trong repo** ở `projectlibre_contrib/lib` và `projectlibre_contrib/ant-lib`, không cần tải thêm.

### Cài đặt trên Windows

1. Cài JDK 21, ví dụ bằng winget:
   ```powershell
   winget install EclipseAdoptium.Temurin.21.JDK
   ```
2. Tải Apache Ant từ https://ant.apache.org/bindownload.cgi, giải nén vào ví dụ `C:\tools\apache-ant`.
3. Đặt biến môi trường `JAVA_HOME` (thư mục JDK), `ANT_HOME` (thư mục Ant) và thêm `%JAVA_HOME%\bin`, `%ANT_HOME%\bin` vào `PATH`.
4. Mở terminal mới và kiểm tra:
   ```powershell
   java -version
   ant -version
   ```

### Cài đặt trên macOS / Linux

```bash
# macOS
brew install openjdk@21 ant

# Ubuntu / Debian
sudo apt install openjdk-21-jdk ant
```

### Mở dự án trong IDE

- **IntelliJ IDEA**: *File → Open* thư mục gốc repo. Vào *Project Structure → SDKs* chọn JDK 21 (file cấu hình đang trỏ SDK tên `homebrew-21`, cần đổi sang JDK trên máy bạn).
- **Eclipse**: *File → Import → Existing Projects into Workspace*, chọn thư mục gốc.
- Chạy/debug với main class `com.projectlibre1.main.Main`, classpath gồm source của `core`, `ui`, `reports`, `exchange` và các jar trong `projectlibre_contrib`.

## Build

Mọi lệnh build chạy trong thư mục `projectlibre_build`:

```bash
cd projectlibre_build
ant
```

Target mặc định `dist` sẽ:

1. Build `projectlibre_contrib` (biên dịch + ProGuard) ra các jar `projectlibre-*.jar`
2. Biên dịch toàn bộ source vào `projectlibre_build/build/`
3. Đóng gói `projectlibre_build/dist/projectlibre.jar` và chép các jar contrib vào `dist/lib/`

> **Lưu ý:** bước 1 ghi đè các jar đã commit trong `projectlibre_contrib/` (kể cả `tmp/`), nên sau khi build `git status` sẽ báo các file này thay đổi. Nếu không sửa gì trong `projectlibre_contrib/src` thì không commit chúng, hoặc bỏ qua bước này bằng cách dùng jar sẵn có (xem bên dưới).

### Các target thường dùng

| Lệnh | Kết quả |
|---|---|
| `ant` / `ant dist` | `dist/projectlibre.jar` + `dist/lib/*.jar` |
| `ant fatjar` | Một jar duy nhất chứa mọi thứ: `packages/projectlibre-<version>.jar` |
| `ant dir` | Thư mục phân phối `packages/projectlibre-<version>/` kèm script chạy và license |
| `ant zip` / `ant tar` | Nén thư mục phân phối (`tar` yêu cầu `gtar`, chủ yếu dùng trên macOS) |
| `ant jpackage-msi` | Chuẩn bị nguồn cho bộ cài MSI Windows trong `packages/jpackage-msi/` (sau đó chạy `make.ps1` ở đó) |
| `ant jpackage-dmg` / `ant jpackage-deb` | Chuẩn bị bộ cài macOS / Debian bằng jpackage |
| `ant clean` | Xóa `build/`, `dist/`, `packages/` |

### Build mà không build lại contrib

Nếu chỉ sửa code trong `core`/`ui`/`reports`/`exchange`, có thể biên dịch thẳng với các jar trong `projectlibre_contrib/lib` để không đụng tới jar đã commit:

```bash
ant -Dbuild_contrib=false dist
```

Khi đó `projectlibre.jar` chứa luôn mã contrib nhưng **không** kèm thư viện bên thứ ba trong manifest, nên chủ yếu dùng để kiểm tra biên dịch.

### Đổi phiên bản

Sửa `version`, `version_name`, `numericVersion3`, `numericVersion4`, `rpm_version`, `deb_version` trong [build.properties](projectlibre_build/build.properties).

## Chạy ứng dụng

Sau khi `ant dist`:

```bash
cd projectlibre_build/dist
java -Xms128m -Xmx768m -jar projectlibre.jar
```

Hoặc chạy bản fat jar:

```bash
java -jar projectlibre_build/packages/projectlibre-1.9.9.jar
```

File dự án mẫu (`.pod`, `.mpp`) để thử nằm trong [projectlibre_build/resources/samples](projectlibre_build/resources/samples).

## Bản dịch (i18n)

Chuỗi giao diện nằm ở hai nhóm file properties:

- `projectlibre_core/src/com/projectlibre1/strings/client_<locale>.properties`
- `projectlibre_ui/src/com/projectlibre1/menu/menu_<locale>.properties`

Khi thêm key mới vào `client.properties` / `menu.properties` (tiếng Anh), nhớ thêm bản dịch tương ứng vào file `_vi`. File được lưu UTF-8; phần lớn chuỗi tiếng Việt hiện có được ghi dạng escape `\uXXXX`, nên giữ cách này cho key mới để an toàn với mọi cách nạp properties.

## Đóng góp

- Nhánh chính: `main`. Tạo nhánh `feature/<tên-ngắn>` cho mỗi thay đổi rồi mở Pull Request.
- Commit message ngắn gọn, động từ ở đầu câu, tiếng Anh (theo lịch sử repo), ví dụ: `Add task name filter that keeps parent summaries`.
- Chạy `ant dist` thành công trước khi mở PR.
- Không commit thư mục `build/`, `dist/`, `packages/` (đã có trong `.gitignore`).

## Giấy phép

ProjectLibre được phát hành theo **Common Public Attribution License 1.0 (CPAL)** — xem [projectlibre_build/license](projectlibre_build/license). Điều khoản attribution của CPAL yêu cầu giữ logo "ProjectLibre" trên mọi màn hình giao diện (căn trái, cạnh menu File, tối thiểu 144×31 px, click dẫn về http://www.projectlibre.com). Mọi bản phát hành lại từ repo này phải tuân thủ yêu cầu đó.

## Tham khảo

- Trang chủ: https://www.projectlibre.com
- Mã nguồn gốc: https://sourceforge.net/projects/projectlibre/
- Hướng dẫn build gốc (cũ): [projectlibre_build/doc/building.html](projectlibre_build/doc/building.html)
