#!/usr/bin/perl
# Sinh API.md (goc repo) tu dac ta OpenAPI cua app + quet @PreAuthorize tu
# source Controller de dien cot Auth (thong tin nay khong co trong OpenAPI).
#
# Cach dung (xem README > "Tai lieu API"):
#   1) Chay app (docker compose up -d --build  HOAC  mvn spring-boot:run)
#   2) perl scripts/gen-api-md.pl
#      - khong co tham so: tu tai spec tu http://localhost:8080/v3/api-docs
#      - hoac truyen duong dan file spec co san: perl scripts/gen-api-md.pl spec.json
#
# Yeu cau: perl + curl (ca 2 co san trong Git Bash tren Windows / moi Linux/macOS).
use strict; use warnings; use utf8; use feature 'unicode_strings';
use JSON::PP qw(decode_json);
use File::Basename qw(dirname);
use Cwd qw(abs_path);

my $ROOT = dirname(dirname(abs_path($0))); # script nam trong ROOT/scripts/
my $OUT  = "$ROOT/API.md";
my $CTRL = "$ROOT/pps-education-backend/src/main/java/vn/com/pps/education/controller";

my $SPEC = $ARGV[0];
if (!$SPEC) {
    my $tmpdir = $ENV{TEMP} // $ENV{TMPDIR} // '/tmp';
    $SPEC = "$tmpdir/pps-openapi-dump.json";
    system('curl', '-sf', 'http://localhost:8080/v3/api-docs', '-o', $SPEC) == 0
        or die "Khong tai duoc http://localhost:8080/v3/api-docs — app da chay chua?\n"
             . "Hoac truyen san file spec: perl scripts/gen-api-md.pl <openapi.json>\n";
}

# ---------- 1. Quet @PreAuthorize tu source ----------
my (%classPerm, %methodPerm); # {Class} = perm ; {Class}{method} = perm
opendir(my $dh, $CTRL) or die "Khong thay thu muc controller: $CTRL\n";
for my $f (grep { /\.java$/ } readdir $dh) {
    my ($class) = $f =~ /^(\w+)\.java$/;
    open my $fh, '<', "$CTRL/$f" or die $!;
    my $pending; my $seenClass = 0;
    while (my $line = <$fh>) {
        if ($line =~ /\@PreAuthorize\("hasPermission\(null,\s*'([^']+)'\)"\)/) {
            if ($seenClass) { $pending = $1; } else { $classPerm{$class} = $1; }
        }
        $seenClass = 1 if $line =~ /public\s+class\s+\Q$class\E/;
        if ($seenClass && $line =~ /public\s+[\w<>,.\[\]? ]+\s+(\w+)\s*\(/) {
            my $m = $1;
            next if $m eq $class; # constructor
            $methodPerm{$class}{$m} = $pending if defined $pending;
            $pending = undef;
        }
    }
    close $fh;
}
closedir $dh;

# ---------- 2. Doc spec ----------
local $/;
open my $sf, '<:raw', $SPEC or die "Khong doc duoc spec: $SPEC\n";
my $d = decode_json(<$sf>);
close $sf;
my $schemas = $d->{components}{schemas} || {};

# ---------- helpers ----------
sub kebab2class { my $t = shift; $t =~ s/(^|-)(\w)/\U$2/g; return $t; }
# Mo phong thuat toan anchor cua GitHub: lowercase, bo ky tu khong phai chu/so/
# khoang trang/gach ngang (GIU nguyen chu unicode co dau), khoang trang -> "-".
sub anchor {
    my $s = lc(shift);
    $s =~ s/[^\p{Alnum}\s-]//g;
    $s =~ s/\s+/-/g;
    return $s;
}
sub schemalink { my $n = shift; return "[$n](#" . anchor($n) . ")"; }
sub typ {
    my $s = shift; return 'object' unless ref $s eq 'HASH';
    if (my $r = $s->{'$ref'}) { $r =~ s|.*/||; return schemalink($r); }
    my $t = $s->{type} // 'object';
    return 'mảng ' . typ($s->{items} || {}) if $t eq 'array';
    if (my $e = $s->{enum}) {
        my @v = @$e; my $more = @v > 6 ? ' \| ...' : '';
        @v = @v[0..5] if @v > 6;
        return 'enum: ' . join(' \| ', @v) . $more;
    }
    $t .= " ($s->{format})" if $s->{format} && $s->{format} =~ /^(int64|date|date-time|binary|uuid)$/;
    return $t;
}
sub respcell {
    my $op = shift;
    my $rs = $op->{responses} or return '—';
    my ($code) = grep { /^2/ } sort keys %$rs;
    return '—' unless $code;
    my $c = $rs->{$code}{content} or return "$code (không có body)";
    my ($mt) = sort keys %$c;
    my $sc = $c->{$mt}{schema} or return "$code (không có body)";
    return typ($sc);
}
sub inputcell {
    my ($op, $path) = @_;
    my @parts;
    if (my $rb = $op->{requestBody}) {
        my $content = $rb->{content} || {};
        if (my $j = $content->{'application/json'}) { push @parts, 'Body: ' . typ($j->{schema}); }
        if (my $m = $content->{'multipart/form-data'}) {
            my $props = $m->{schema}{properties} || {};
            my @fs = map { "`$_`" . ((($props->{$_}{format}//'') eq 'binary') ? ' (tệp)' : '') } sort keys %$props;
            push @parts, 'Form-data: ' . join(', ', @fs);
        }
    }
    my (@q, @h);
    for my $p (@{$op->{parameters} || []}) {
        my $tag = '`' . $p->{name} . '`' . ($p->{required} ? '' : '?');
        push @q, $tag if $p->{in} eq 'query';
        push @h, $tag if $p->{in} eq 'header';
    }
    push @parts, 'Query: ' . join(', ', @q) if @q;
    push @parts, 'Header: ' . join(', ', @h) if @h;
    return @parts ? join('<br>', @parts) : '—';
}
sub authcell {
    my ($path, $tagclass, $method) = @_;
    return 'Công khai' if $path =~ m{^/api/auth/};
    return 'Header `X-Webhook-Secret`' if $path =~ m{^/api/webhooks/};
    $method =~ s/_\d+$//;
    my $perm = $methodPerm{$tagclass}{$method} // $classPerm{$tagclass};
    return $perm ? "JWT + `$perm`" : 'JWT';
}

# ---------- 3. Nhom operation theo tag ----------
my %byTag;
for my $path (keys %{$d->{paths}}) {
    for my $m (grep { /^(get|post|put|delete|patch)$/ } keys %{$d->{paths}{$path}}) {
        my $op = $d->{paths}{$path}{$m};
        my $tag = $op->{tags}[0] // 'khac';
        push @{$byTag{$tag}}, { path => $path, method => uc($m), op => $op };
    }
}

# ---------- 4. Ten hien thi + thu tu nhom ----------
my @order = (
    ['auth-controller',                 'Xác thực (UC-01)'],
    ['user-controller',                 'Khởi tạo tài khoản người dùng (UC-43)'],
    ['permission-controller',           'Danh mục quyền (UC-02)'],
    ['role-controller',                 'Nhóm quyền mặc định (UC-03)'],
    ['user-permission-override-controller', 'Quyền ngoại lệ theo tài khoản (UC-04)'],
    ['permission-audit-log-controller', 'Nhật ký phân quyền (UC-05)'],
    ['task-controller',                 'Quản lý công việc (UC-06/07)'],
    ['employee-controller',             'Hồ sơ nhân sự, hợp đồng, bằng cấp (UC-08)'],
    ['attendance-controller',           'Chấm công nhân sự (UC-09)'],
    ['leave-request-controller',        'Đơn từ (UC-10/11)'],
    ['payroll-controller',              'Bảng lương (UC-12)'],
    ['student-controller',              'Hồ sơ học sinh & trạng thái học tập (UC-13/14)'],
    ['student-attendance-controller',   'Điểm danh học sinh (UC-15)'],
    ['student-batch-import-controller', 'Import học sinh từ Excel (UC-35)'],
    ['curriculum-controller',           'Khung chương trình (UC-16/16b/17)'],
    ['class-controller',                'Lớp học, giáo viên, ghi danh (UC-18)'],
    ['class-session-controller',        'Buổi học / xếp lịch'],
    ['grade-controller',                'Sổ điểm & duyệt điểm (UC-19/20)'],
    ['student-comment-controller',      'Nhận xét học sinh (UC-21/22)'],
    ['lesson-controller',               'Bài giảng (LMS)'],
    ['question-bank-controller',        'Ngân hàng câu hỏi (UC-40)'],
    ['exercise-controller',             'Soạn & giao đề (UC-40)'],
    ['exercise-attempt-controller',     'Làm bài & nộp bài (LMS)'],
    ['manual-grading-controller',       'Chấm bài thủ công (UC-41)'],
    ['teaching-plan-controller',        'Kế hoạch giảng dạy'],
    ['portal-class-selection-controller','Chọn lớp đang xem — cổng HS/PH (UC-42)'],
    ['parent-portal-controller',        'Cổng phụ huynh'],
    ['notification-controller',         'Thông báo'],
    ['tuition-plan-controller',         'Biểu phí học phí'],
    ['scholarship-controller',          'Học bổng'],
    ['invoice-controller',              'Hóa đơn & thanh toán (UC-30)'],
    ['operating-expense-controller',    'Chi phí vận hành (UC-31)'],
    ['finance-report-controller',       'Báo cáo tài chính (UC-32)'],
    ['lead-controller',                 'Lead & chuyển đổi tuyển sinh (UC-33/34)'],
    ['site-controller',                 'Điểm trường (UC-36)'],
    ['partner-contract-controller',     'Hợp đồng trường liên kết (UC-36b)'],
    ['room-controller',                 'Phòng học (UC-37)'],
    ['equipment-controller',            'Thiết bị dạy học (UC-37)'],
    ['partner-feedback-controller',     'Phản hồi trường liên kết (UC-38/39)'],
    ['partner-portal-controller',       'Cổng trường liên kết'],
);
my %named = map { $_->[0] => 1 } @order;
push @order, map { [$_, $_] } grep { !$named{$_} } sort keys %byTag;

# ---------- 5. Xuat markdown ----------
open my $out, '>:encoding(UTF-8)', $OUT or die $!;
print $out <<'HDR';
# Tài liệu API — PPS Education Backend

> File này được sinh từ đặc tả OpenAPI của app (`GET /v3/api-docs`, springdoc)
> bằng `scripts/gen-api-md.pl` — xem README > "Tài liệu API" để biết cách sinh
> lại khi API thay đổi. Nguồn sống & luôn mới nhất: chạy app rồi mở **Swagger
> UI** (http://localhost:8080/swagger-ui.html); file này là bản chụp để đọc
> offline. KHÔNG sửa tay file này — sửa sẽ bị ghi đè lần sinh sau.

## Quy ước chung

- **Base URL** (dev local): `http://localhost:8080`
- **Xác thực**: trừ nhóm `Công khai`, mọi endpoint yêu cầu header
  `Authorization: Bearer <accessToken>`. Lấy token qua `POST /api/auth/login`
  (access token sống 15 phút — trường `accessTokenExpiresInSeconds`; làm mới
  bằng `POST /api/auth/refresh` với refresh token, sống 14 ngày).
- **Cột Auth**: `JWT + permission` nghĩa là ngoài JWT còn cần permission code đó
  trong effective permissions (Hybrid PBAC — role mặc định hoặc override UC-04).
  Một số endpoint chỉ ghi `JWT` nhưng vẫn kiểm tra phạm vi dữ liệu trong Service
  (VD: đúng site quản lý, đúng lớp được phân công, đúng con của phụ huynh).
- **Tài khoản demo** cho dev local: xem mục "Tài khoản demo" trong [README](./README.md).
- Ký hiệu trong cột Input: `tên?` = tham số không bắt buộc. Kiểu dữ liệu của
  body/response xem chi tiết ở [Phụ lục: Schemas](#phụ-lục-schemas).

HDR

# Muc luc
print $out "## Mục lục\n\n";
for my $o (@order) {
    my ($tag, $title) = @$o;
    next unless $byTag{$tag};
    print $out "- [$title](#" . anchor($title) . ")\n";
}
print $out "- [Phụ lục: Schemas](#" . anchor('Phụ lục: Schemas') . ")\n\n---\n\n";

for my $o (@order) {
    my ($tag, $title) = @$o;
    my $ops = $byTag{$tag} or next;
    my $class = kebab2class($tag);
    print $out "## $title\n\n";
    print $out "| Method | Path | Auth | Input | Output |\n|---|---|---|---|---|\n";
    for my $e (sort { $a->{path} cmp $b->{path} || $a->{method} cmp $b->{method} } @$ops) {
        my ($p, $m, $op) = ($e->{path}, $e->{method}, $e->{op});
        my $auth = authcell($p, $class, $op->{operationId} // '');
        my $in  = inputcell($op, $p);
        my $outv = respcell($op);
        print $out "| $m | `$p` | $auth | $in | $outv |\n";
    }
    print $out "\n";
}

# Phu luc schemas
print $out "---\n\n## Phụ lục: Schemas\n\n";
for my $name (sort keys %$schemas) {
    my $s = $schemas->{$name};
    print $out "### $name\n\n";
    my $props = $s->{properties};
    if (!$props || !%$props) { print $out "_(không có trường — object rỗng hoặc kiểu đặc biệt)_\n\n"; next; }
    my %req = map { $_ => 1 } @{$s->{required} || []};
    print $out "| Trường | Kiểu | Bắt buộc |\n|---|---|---|\n";
    for my $f (sort keys %$props) {
        my $t = typ($props->{$f});
        print $out "| `$f` | $t | " . ($req{$f} ? '✔' : '') . " |\n";
    }
    print $out "\n";
}
close $out;
print "OK: da sinh $OUT\n";
