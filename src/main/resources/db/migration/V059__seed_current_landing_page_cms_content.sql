-- The public home page already exists in the website. Register that existing
-- page in CMS so editors can update it instead of creating a second landing page.
insert into public.cms_pages (
  slug,
  title,
  excerpt,
  content,
  seo_metadata,
  status,
  published_at
)
select
  'trang-chu',
  'Trang chủ IELTS',
  'Chinh phục IELTS bằng một lộ trình rõ ràng, phương pháp học thuật tinh gọn và sự đồng hành sát sao.',
  $$
  {
    "sections": [
      {
        "id": "hero",
        "type": "hero",
        "label": "Hero trang chủ",
        "eyebrow": "The IELTS Spells • Học Thuật Kỳ Diệu",
        "title": "Cast the Spells, Claim the Band",
        "description": "Chinh phục IELTS bằng một lộ trình rõ ràng, kết hợp giữa phương pháp học thuật tinh gọn và sự hỗ trợ từ các giáo viên từ 8.0 - 8.5 IELTS hàng đầu.",
        "primaryCta": "Tư Vấn & Test Đầu Vào",
        "secondaryCta": "Khám Phá Khóa Học",
        "mediaPath": "/banner.png",
        "enabled": true
      },
      {
        "id": "about",
        "type": "feature",
        "label": "Giới thiệu",
        "eyebrow": "Về The IELTS Spells",
        "title": "Không chỉ học IELTS — bạn học cách tiến bộ",
        "description": "Mỗi học viên đều sở hữu tiềm năng bứt phá. The IELTS Spells mang tới giải pháp tinh gọn, giúp bạn chinh phục bằng tư duy học thuật thực chất thay vì áp lực học mẹo.",
        "mediaPath": "/gioithieu.png",
        "enabled": true
      },
      {
        "id": "benefits",
        "type": "feature",
        "label": "Quyền lợi học viên",
        "eyebrow": "Quyền Lợi Học Viên",
        "title": "Đồng hành sát sao & theo dõi tiến độ học tập thực tế",
        "description": "Trải nghiệm học tập minh bạch, nơi sự tiến bộ được đo lường chính xác và hỗ trợ liên tục.",
        "mediaPath": "/kiemtratiendo.jpg",
        "enabled": true
      },
      {
        "id": "courses",
        "type": "feature",
        "label": "Khóa học",
        "eyebrow": "Lộ trình học",
        "title": "Chọn đúng khóa học cho mục tiêu band điểm",
        "description": "Các chương trình IELTS được thiết kế theo trình độ đầu vào, mục tiêu đầu ra và nhịp học phù hợp.",
        "enabled": true
      },
      {
        "id": "roadmap",
        "type": "feature",
        "label": "Lộ trình đào tạo",
        "eyebrow": "Lộ Trình Đào Tạo",
        "title": "Hành trình bứt phá từ mất gốc đến Band điểm ước mơ",
        "description": "Mỗi giai đoạn được thiết kế đồng bộ giúp bạn tự tin tiến bước mà không bị ngợp hay nản lòng.",
        "enabled": true
      },
      {
        "id": "teachers",
        "type": "feature",
        "label": "Đội ngũ giảng viên",
        "eyebrow": "Đội Ngũ Giảng Viên",
        "title": "Gặp gỡ các pháp sư IELTS đồng hành",
        "description": "100% giảng viên sở hữu IELTS 8.0 - 8.5+ Overall và trực tiếp theo sát lộ trình của học viên.",
        "enabled": true
      },
      {
        "id": "feedback",
        "type": "social-proof",
        "label": "Phản hồi học viên",
        "eyebrow": "Câu chuyện học viên",
        "title": "Tiến bộ thật, phản hồi thật",
        "description": "Những thay đổi trong điểm số và tư duy học tập của học viên là minh chứng cho một lộ trình đúng.",
        "enabled": true
      },
      {
        "id": "consultation",
        "type": "cta",
        "label": "Tư vấn miễn phí",
        "eyebrow": "Tư Vấn Miễn Phí",
        "title": "Nhận tư vấn lộ trình & test đầu vào 0đ",
        "description": "Đội ngũ cố vấn chuyên môn sẽ đánh giá hổng kiến thức và đề xuất lộ trình tinh gọn nhất cho mục tiêu của bạn.",
        "primaryCta": "Đăng ký tư vấn",
        "enabled": true
      }
    ]
  }
  $$::jsonb,
  $$
  {
    "title": "Trang chủ IELTS | The IELTS Spells",
    "description": "Lộ trình học IELTS tinh gọn, rõ ràng và sát mục tiêu band điểm."
  }
  $$::jsonb,
  'PUBLISHED'::public.publish_status,
  now()
where not exists (
  select 1 from public.cms_pages where slug = 'trang-chu'
);
