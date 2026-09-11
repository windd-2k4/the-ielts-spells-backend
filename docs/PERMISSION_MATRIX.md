# Permission Matrix

## 1. Mục tiêu

Tài liệu này là baseline phân quyền cho sáu role đăng nhập của The IELTS Spells:

- `ADMIN` — Quản trị viên.
- `ADMISSIONS` — Sales / Tư vấn tuyển sinh.
- `SOCIAL_MEDIA` — Social Media / Truyền thông và nội dung công khai.
- `TEACHER` — Giáo viên.
- `STUDENT_SUPPORT` — Hỗ trợ học viên trong các khóa được phân công.
- `STUDENT` — Học viên.

Khách vãng lai là người dùng chưa đăng nhập (`anonymous`), không phải một giá trị của `app_role`.

Tài liệu mô tả mô hình đích. Việc đổi enum, API guard, route guard hoặc dữ liệu hiện hữu phải được thực hiện bằng migration và thay đổi mã nguồn riêng sau khi ma trận được duyệt.

## 2. Nguyên tắc phân quyền

1. **Default deny**: không có quyền rõ ràng thì từ chối.
2. **Backend là nguồn quyết định cuối cùng**: ẩn menu hoặc nút ở frontend chỉ phục vụ UX, không thay thế authorization tại API.
3. **Quyền luôn đi cùng phạm vi dữ liệu**: toàn hệ thống, khóa học được phân công, hồ sơ do mình phụ trách hoặc dữ liệu của chính mình.
4. **Ít quyền nhất**: Social Media không đọc dữ liệu học viên; ADMISSIONS không quản trị nhân sự hoặc xuất bản đề; Giáo viên không mặc nhiên truy cập mọi lớp.
5. **Tách hành động nhạy cảm**: xuất bản, phê duyệt, mở lại dữ liệu đã khóa, thay đổi role và khóa tài khoản phải có permission riêng.
6. **Nhiều role lấy hợp quyền**: nếu một tài khoản có nhiều role trong `user_roles`, quyền hiệu lực là hợp của các role, nhưng mọi giới hạn phạm vi dữ liệu vẫn được áp dụng.
7. **Ghi audit** cho thay đổi role/trạng thái tài khoản, publish/archive, chuyển lớp, bảo lưu, thay đổi trạng thái ghi danh, khóa/mở lại điểm danh và thao tác chấm điểm.

## 3. Ký hiệu

| Ký hiệu | Ý nghĩa |
|---|---|
| `F` | Toàn quyền trên toàn bộ dữ liệu của chức năng |
| `M` | Được tạo/cập nhật theo phạm vi ghi trong ô |
| `R` | Chỉ được xem theo phạm vi ghi trong ô |
| `Q` | Được gửi yêu cầu hoặc khởi tạo quy trình, không được tự phê duyệt |
| `O` | Chỉ dữ liệu của chính người dùng |
| `A` | Chỉ khóa học/lớp được phân công hoặc đang ghi danh |
| `—` | Không có quyền |

## 4. Ma trận quyền mục tiêu

### 4.1. Identity, nhân sự và hệ thống

| Permission | ADMIN | ADMISSIONS | SOCIAL_MEDIA | TEACHER | STUDENT_SUPPORT | STUDENT |
|---|---:|---:|---:|---:|---:|---:|
| `identity.profile.self.read` — xem hồ sơ của mình | O | O | O | O | O | O |
| `identity.profile.self.update` — sửa thông tin cá nhân an toàn | O | O | O | O | O | O |
| `identity.staff.read` — xem đầy đủ danh sách/hồ sơ nhân sự | F | — | — | — | — | — |
| `identity.staff.manage` — mời, cập nhật, khóa nhân sự | F | — | — | — | — | — |
| `identity.role.manage` — cấp hoặc thu hồi role | F | — | — | — | — | — |
| `identity.teacher_options.read` — xem danh sách giáo viên đang hoạt động để phân công | F | — | — | — | — | — |
| `audit.read` — xem nhật ký kiểm toán | R | — | — | — | — | — |
| `system.settings.manage` — cấu hình hệ thống/tích hợp | F | — | — | — | — | — |

Không cho phép người dùng tự sửa email đăng nhập, role, trạng thái hoạt động hoặc các trường nội bộ qua `identity.profile.self.update`.

### 4.2. Lead, học viên và ghi danh

| Permission | ADMIN | ADMISSIONS | SOCIAL_MEDIA | TEACHER | STUDENT_SUPPORT | STUDENT |
|---|---:|---:|---:|---:|---:|---:|
| `admissions.lead.read` — xem lead và thông tin liên hệ | F | R | — | — | — | — |
| `admissions.lead.manage` — cập nhật trạng thái, nguồn và người phụ trách | F | M | — | — | — | — |
| `admissions.lead.convert` — chuyển lead thành học viên | F | M | — | — | — | — |
| `student.profile.read` — xem hồ sơ học viên | F | R (phục vụ tuyển sinh) | — | R (A, dữ liệu học tập cần thiết) | —* | O |
| `student.profile.update_contact` — cập nhật liên hệ cơ bản | F | M (trước/bàn giao ghi danh) | — | — | — | O |
| `student.profile.update_academic` — cập nhật dữ liệu học vụ/nội bộ | F | — | — | — | — | — |
| `enrollment.read` — xem ghi danh | F | R (hồ sơ phụ trách) | — | R (A) | R (A) | O |
| `enrollment.create` — tạo ghi danh ban đầu | F | M | — | — | — | — |
| `enrollment.lifecycle.request` — yêu cầu bảo lưu/chuyển lớp | F | Q | — | — | — | Q (O, khi có UI/API) |
| `enrollment.lifecycle.approve` — duyệt/từ chối bảo lưu hoặc chuyển lớp | F | — | — | — | — | — |
| `enrollment.status.manage` — kích hoạt, tạm dừng, hoàn tất, rút học | F | — | — | — | — | — |
| `enrollment.exam_plan.manage` — cập nhật kế hoạch thi IELTS | F | M (giai đoạn tư vấn) | — | R (A) | — | O (nếu cho tự cập nhật) |

`ADMISSIONS` được tạo ghi danh và bàn giao, nhưng không tự duyệt bảo lưu/chuyển lớp hoặc thay đổi lifecycle sau khi học viên đã vào vận hành. Những hành động nhạy cảm thuộc `ADMIN`. `STUDENT_SUPPORT` dùng API scope riêng để xem danh sách liên hệ tối thiểu của học viên trong khóa được gán (ký hiệu `—*`), không dùng API hồ sơ học viên toàn cục.

### 4.3. Khóa học, lịch học, điểm danh và tiến độ

| Permission | ADMIN | ADMISSIONS | SOCIAL_MEDIA | TEACHER | STUDENT_SUPPORT | STUDENT |
|---|---:|---:|---:|---:|---:|---:|
| `course.read` — xem khóa học | F | R (khóa đang/sắp tuyển) | — | R (A) | R (A) | R (A) |
| `course.manage` — tạo, cập nhật, ngừng hoạt động khóa | F | — | — | — | — | — |
| `course.teacher.assign` — phân công giáo viên | F | — | — | — | — | — |
| `course.student_support.assign` — phân công Student Support | F | — | — | — | — | — |
| `schedule.template.manage` — quản lý mẫu lịch | F | — | — | — | — | — |
| `session.read` — xem buổi học | F | R (phục vụ tư vấn) | — | R (A) | R (A) | R (A) |
| `session.manage` — tạo, sửa, đổi lịch, hủy buổi học | F | — | — | — | — | — |
| `attendance.read` — xem điểm danh | F | — | — | R (A) | R (A) | O |
| `attendance.mark` — khởi tạo, lưu nháp, xác nhận điểm danh | F | — | — | M (A) | — | — |
| `attendance.reopen` — mở lại phiếu đã khóa, bắt buộc có lý do | F | — | — | — | — | — |
| `progress.read` — xem tiến độ học tập | F | — | — | R (A) | R (A) | O |

`TEACHER` phải được kiểm tra membership tại service/query, không chỉ kiểm tra authority `teacher` ở controller.

### 4.4. Học liệu, Media và ngân hàng đề

| Permission | ADMIN | ADMISSIONS | SOCIAL_MEDIA | TEACHER | STUDENT_SUPPORT | STUDENT |
|---|---:|---:|---:|---:|---:|---:|
| `library.published.read` — xem học liệu đã xuất bản | F | — | — | R | — | R (A) |
| `library.draft.create` — tạo học liệu/bài tập nháp | F | — | — | M | — | — |
| `library.draft.update_own` — sửa/archive bản nháp của mình | F | — | — | M (O) | — | — |
| `library.publish` — xuất bản/archive học liệu dùng chung | F | — | — | — | — | — |
| `academic_media.manage_own` — upload/xóa media học thuật do mình tạo | F | — | — | M (O) | — | — |
| `test.read` — xem ngân hàng đề | F | — | — | R | — | — |
| `test.draft.create` — tạo đề nháp | F | — | — | M | — | — |
| `test.draft.update_own` — sửa, gửi duyệt, tạo revision đề của mình | F | — | — | M (O) | — | — |
| `test.publish` — trả về nháp, publish hoặc archive đề đã publish | F | — | — | — | — | — |
| `test.assignment.manage` — giao/archive bài kiểm tra | F | — | — | M (A) | — | — |
| `assessment.result.read` — xem kết quả bài làm | F | — | — | R (A) | R (trạng thái/điểm, không xem đáp án riêng tư nếu không cần) | O |
| `assessment.grade` — chấm và phản hồi học thuật | F | — | — | M (A) | — | — |
| `assessment.attempt` — bắt đầu, lưu và nộp bài | — | — | — | — | — | O |

Media học thuật và Media truyền thông phải là hai scope riêng. Không cấp `SOCIAL_MEDIA` quyền vào file đề thi, đáp án, bài làm hoặc tài liệu nội bộ chỉ vì cả hai cùng dùng Supabase Storage.

### 4.5. CMS, truyền thông và báo cáo

| Permission | ADMIN | ADMISSIONS | SOCIAL_MEDIA | TEACHER | STUDENT_SUPPORT | STUDENT |
|---|---:|---:|---:|---:|---:|---:|
| `cms.content.read` — xem nội dung CMS | F | R (bản published) | R | — | — | — |
| `cms.content.manage` — tạo/sửa/lên lịch nội dung | F | — | M | — | — | — |
| `cms.content.publish` — publish/unpublish/archive nội dung | F | — | M | — | — | — |
| `marketing_media.manage` — quản lý ảnh/video truyền thông | F | — | M | — | — | — |
| `report.executive.read` — báo cáo tổng hợp toàn hệ thống | R | — | — | — | — | — |
| `report.admissions.read` — báo cáo lead/chuyển đổi | F | R | R (chỉ số tổng hợp, không PII) | — | — | — |
| `report.academic.read` — báo cáo học tập/lớp học | F | — | — | R (A) | — | O |
| `report.operations.read` — báo cáo vận hành, lịch và điểm danh | F | — | — | R (A) | — | — |

## 5. Quyền anonymous của khách vãng lai

| Hành động | Quyền |
|---|---|
| Xem trang chủ, nội dung và khóa học công khai | Cho phép |
| Gửi form đăng ký tư vấn/lead | Cho phép, có rate limit và chống spam |
| Đăng nhập, quên mật khẩu, kích hoạt lời mời hợp lệ | Cho phép |
| Xem CMS draft, dữ liệu học viên, lớp học, học liệu nội bộ hoặc đề thi | Từ chối |
| Truy cập `/api/v1/admin/**` hoặc `/api/v1/student/**` | Từ chối |

## 6. Các endpoint hiện tại cần tách quyền khi triển khai

Một số controller hiện đặt role ở cấp class nên đang cấp quyền rộng hơn baseline mục tiêu. Khi triển khai phải tách guard theo action:

- `StudentAdminController`: tách quyền đọc, cập nhật liên hệ và cập nhật học vụ; không để `ADMISSIONS` sửa toàn bộ hồ sơ.
- `EnrollmentAdminController`: tách tạo ghi danh, xem, cập nhật trạng thái và cập nhật kế hoạch thi.
- `StudentLifecycleController`: `ADMISSIONS` chỉ gửi yêu cầu; `ADMIN` mới được duyệt hoặc từ chối.
- `CourseAdminController`, `ScheduleTemplateAdminController`, `ClassSessionAdminController`: chỉ `ADMIN` vận hành toàn cục; Giáo viên và Student Support chỉ đọc đúng khóa được phân công khi endpoint hỗ trợ scope đó.
- `AttendanceAdminController`: Giáo viên điểm danh lớp được phân công; `STUDENT_SUPPORT` chỉ xem chuyên cần ở khóa được gán.
- `LearningLibraryAdminController`: tách read, own-draft mutation, publish và quyền file; hiện class-level guard cho phép mọi giáo viên gọi toàn bộ CRUD.
- `TestBankAdminController`: giữ owner rule cho Giáo viên; chỉ `ADMIN` là moderator publish sau khi bỏ `MANAGER`.
- `TestAssignmentAdminController`: Giáo viên chỉ thao tác khóa được phân công; Student Support không giao hoặc archive bài kiểm tra.
- CMS có API quản trị riêng tại `/api/v1/social-media/cms/**` cho `SOCIAL_MEDIA`; `PublicContentController` chỉ là surface public đọc-only.

## 7. Tên role giữa các lớp hệ thống

| Lớp | Quy ước |
|---|---|
| PostgreSQL enum / Java enum | `ADMIN`, `ADMISSIONS`, `SOCIAL_MEDIA`, `TEACHER`, `STUDENT_SUPPORT`, `STUDENT` |
| JWT `user_roles` | Có thể chứa enum uppercase; converter phải chuẩn hóa |
| Spring authority | `admin`, `admissions`, `social_media`, `teacher`, `student_support`, `student` |
| TypeScript role | `"admin" | "admissions" | "social_media" | "teacher" | "student_support" | "student"` |
| Nhãn giao diện | Quản trị viên, Sales/Tuyển sinh, Social Media, Giáo viên, Hỗ trợ học viên, Học viên |

Không đổi `ADMISSIONS` thành `SALES` trong database, Java enum, JWT authority hoặc TypeScript contract. “Sales” chỉ là nhãn nghiệp vụ nếu cần hiển thị trên giao diện.

## 8. Chuyển đổi role hiện tại

| Role hiện tại | Role mục tiêu | Cách xử lý |
|---|---|---|
| `ADMIN` | `ADMIN` | Giữ nguyên |
| `ADMISSIONS` | `ADMISSIONS` | Giữ nguyên |
| `CMS_EDITOR` | `SOCIAL_MEDIA` | Có thể migrate trực tiếp sau khi rà scope Storage/CMS |
| `TEACHER` | `TEACHER` | Giữ nguyên |
| `STUDENT` | `STUDENT` | Giữ nguyên |
| `MANAGER` | `ADMIN` hoặc `STUDENT_SUPPORT` | Duyệt thủ công từng tài khoản; không tự động nâng thành Admin |
| `TEACHING_ASSISTANT` | `TEACHER` hoặc `STUDENT_SUPPORT` | Duyệt theo nhiệm vụ thực tế và phân công khóa học |

## 9. Checklist triển khai sau khi duyệt ma trận

1. Đã tạo `V022` để thêm `SOCIAL_MEDIA`, `STUDENT_SUPPORT`; không sửa migration cũ đã chạy.
2. Đã tạo `V023` để migrate `CMS_EDITOR`; lập danh sách `MANAGER` và `TEACHING_ASSISTANT` cần quyết định thủ công.
3. Đã cập nhật `AppRole`, staff invitation, staff profile và constraint `access_requests`. Giữ nguyên `course_teachers.teaching_role` cho đến khi hoàn tất quyết định chuyển từng `TEACHING_ASSISTANT`.
4. Converter JWT đã chuẩn hóa authority mới; sau khi chạy migration, người dùng cần đăng nhập lại để nhận claim mới.
5. Đã tạo permission constants/policy tập trung ở backend và thay thế các chuỗi role rải trong controller.
6. Đã tạo `V025` với `course_student_supports`; `STUDENT_SUPPORT` chỉ xem khóa, học viên, chuyên cần và tiến độ qua API scope riêng `/api/v1/student-support/**`.
7. Cập nhật TypeScript contract, auth context, route guards, sidebar, Staff Admin và action guards.
8. Viết test cho từng permission với ít nhất một ca được phép, một ca `403` và một ca truy cập sai scope.
9. Đã thêm `/api/v1/social-media/cms/**` cho pages, posts, banners, campaigns và publication state; `V026` công khai nội dung `SCHEDULED` đúng thời điểm qua RLS.
10. Seed tài khoản mẫu cho từng role và thực hiện smoke test end-to-end.
