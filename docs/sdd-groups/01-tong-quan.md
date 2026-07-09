## Tổng quan

Toàn bộ cơ sở dữ liệu được tổ chức thành 9 nhóm theo cụm nghiệp vụ, thay
vì đi tuần tự theo 10 phân hệ SRS -- Vì nhiều bảng dùng chung giữa các
phân hệ.

  -------------------------------------------------------------------------
  Nhóm   Tên nhóm                    Phân hệ SRS  Số bảng      Số bảng
                                     liên quan    chính        history
  ------ --------------------------- ------------ ------------ ------------
  1      Nền tảng                    1, 2         12           3

  2      Cơ sở vật chất & Điểm       10           7            5
         trường                                                

  3      Học sinh & Phụ huynh        5            5            2

  4      Nhân sự                     4            12           6

  5      Học thuật                   5, 6         15           13

  6      Tài chính & Học phí         8            9            4

  7      Tuyển sinh & CRM            9            2            1

  8      LMS & Portal                7            13           5

  9      Task Management & Thông báo 3            7            2
  -------------------------------------------------------------------------
