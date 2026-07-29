package com.webtro.config;

import com.webtro.common.enums.CategoryCode;
import com.webtro.common.enums.CommentStatus;
import com.webtro.common.enums.CurfewType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.Gender;
import com.webtro.common.enums.GenderRequirement;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.NotificationChannel;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.PaymentMethod;
import com.webtro.common.enums.PaymentStatus;
import com.webtro.common.enums.ReportReason;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.common.enums.ReportStatus;
import com.webtro.common.enums.ReportTargetType;
import com.webtro.common.enums.ReviewStatus;
import com.webtro.common.enums.SentimentLabel;
import com.webtro.common.enums.SubscriptionStatus;
import com.webtro.common.enums.ToiletType;
import com.webtro.common.enums.UserStatus;
import com.webtro.common.enums.VerificationStatus;
import com.webtro.config.properties.AppProperties;
import com.webtro.config.properties.StorageProperties;
import com.webtro.constant.RoleCode;
import com.webtro.modules.catalog.entity.Amenity;
import com.webtro.modules.catalog.entity.Category;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.AmenityRepository;
import com.webtro.modules.catalog.repository.CategoryRepository;
import com.webtro.modules.catalog.repository.DistrictRepository;
import com.webtro.modules.catalog.repository.ProvinceRepository;
import com.webtro.modules.catalog.repository.WardRepository;
import com.webtro.modules.interaction.entity.Comment;
import com.webtro.modules.interaction.entity.Favorite;
import com.webtro.modules.interaction.entity.Review;
import com.webtro.modules.interaction.entity.ViewHistory;
import com.webtro.modules.interaction.repository.CommentRepository;
import com.webtro.modules.interaction.repository.FavoriteRepository;
import com.webtro.modules.interaction.repository.ReviewRepository;
import com.webtro.modules.interaction.repository.ViewHistoryRepository;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.entity.ListingAmenity;
import com.webtro.modules.listing.entity.ListingImage;
import com.webtro.modules.listing.repository.ListingAmenityRepository;
import com.webtro.modules.listing.repository.ListingImageRepository;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.moderation.entity.Report;
import com.webtro.modules.moderation.repository.ReportRepository;
import com.webtro.modules.notification.entity.Notification;
import com.webtro.modules.notification.repository.NotificationRepository;
import com.webtro.modules.payment.entity.Payment;
import com.webtro.modules.payment.entity.PromotionPackage;
import com.webtro.modules.payment.entity.PromotionSubscription;
import com.webtro.modules.payment.repository.PaymentRepository;
import com.webtro.modules.payment.repository.PromotionPackageRepository;
import com.webtro.modules.payment.repository.PromotionSubscriptionRepository;
import com.webtro.modules.user.entity.Follow;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.entity.Role;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.entity.UserProfile;
import com.webtro.modules.user.entity.UserRole;
import com.webtro.modules.user.repository.FollowRepository;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.repository.RoleRepository;
import com.webtro.modules.user.repository.UserProfileRepository;
import com.webtro.modules.user.repository.UserRepository;
import com.webtro.modules.user.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Gieo DỮ LIỆU MẪU THẬT vào cơ sở dữ liệu lúc ứng dụng khởi động — người dùng, tin đăng (kèm ảnh
 * JPEG thật), tiện ích, bình luận, đánh giá, theo dõi, báo cáo, thông báo, thanh toán + đẩy tin.
 * Mục đích: có sẵn dữ liệu phong phú để demo mọi trang (tìm kiếm, chi tiết, AI cảm xúc, kiểm duyệt,
 * doanh thu) mà không phải bấm tay.
 *
 * <p><b>Chạy sau</b> {@link AdminAccountInitializer} ({@code @Order(1)}) để chắc chắn vai trò/role
 * đã sẵn sàng. <b>Idempotent</b>: chỉ chạy khi bật cờ {@code app.seed.demo-enabled} VÀ bảng
 * {@code listings} còn trống — đã có dữ liệu thì bỏ qua, không tạo trùng.
 *
 * <p><b>An toàn khởi động</b>: toàn bộ việc gieo nằm trong một giao dịch ({@link #seed()}), gọi qua
 * proxy để {@code @Transactional} có hiệu lực; nếu lỗi thì rollback và ghi log ERROR nhưng KHÔNG
 * ném ngoại lệ ra {@link ApplicationRunner} (không làm sập ứng dụng).
 *
 * <p><b>Ảnh</b>: sinh JPEG thật bằng {@link BufferedImage} (nền màu + chữ không dấu là tên tin) và
 * ghi thẳng vào thư mục lưu trữ theo đúng quy ước của {@code LocalFileStorage}
 * (file trên đĩa = {@code {root}/{key}.jpg}, URL công khai = {@code {publicBaseUrl}/{key}} không
 * kèm đuôi), gồm 3 kích thước gốc/_medium/_thumb — để {@code FileController} phục vụ được ngay.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    // Mật khẩu dùng chung cho MỌI tài khoản mẫu để tiện đăng nhập demo.
    private static final String DEMO_PASSWORD = "Test@1234";
    private static final String DOMAIN = "@webtro.local";

    private final AppProperties appProperties;
    private final StorageProperties storageProperties;
    private final PasswordEncoder passwordEncoder;
    private final ObjectProvider<DemoDataInitializer> self;

    // user module
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserProfileRepository userProfileRepository;
    private final LandlordProfileRepository landlordProfileRepository;
    private final FollowRepository followRepository;

    // catalog module
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final WardRepository wardRepository;
    private final CategoryRepository categoryRepository;
    private final AmenityRepository amenityRepository;

    // listing module
    private final ListingRepository listingRepository;
    private final ListingImageRepository listingImageRepository;
    private final ListingAmenityRepository listingAmenityRepository;

    // interaction module
    private final FavoriteRepository favoriteRepository;
    private final ViewHistoryRepository viewHistoryRepository;
    private final CommentRepository commentRepository;
    private final ReviewRepository reviewRepository;

    // moderation / notification / payment
    private final ReportRepository reportRepository;
    private final NotificationRepository notificationRepository;
    private final PaymentRepository paymentRepository;
    private final PromotionSubscriptionRepository promotionSubscriptionRepository;
    private final PromotionPackageRepository promotionPackageRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (!appProperties.getSeed().isDemoEnabled()) {
            log.info("Bỏ qua gieo dữ liệu mẫu (app.seed.demo-enabled=false)");
            return;
        }
        if (listingRepository.count() > 0) {
            log.info("Đã có dữ liệu mẫu (listings.count > 0), bỏ qua gieo dữ liệu demo");
            return;
        }
        try {
            // Gọi qua proxy để @Transactional có hiệu lực (tránh self-invocation).
            self.getObject().seed();
        } catch (Exception ex) {
            log.error("Gieo dữ liệu mẫu THẤT BẠI (đã rollback, không làm sập ứng dụng): {}",
                    ex.getMessage(), ex);
        }
    }

    /**
     * Toàn bộ việc gieo trong MỘT giao dịch: hoặc thành công trọn vẹn, hoặc rollback sạch để lần
     * khởi động sau vẫn còn cơ hội gieo lại (idempotent theo {@code listings.count}).
     */
    @Transactional
    public void seed() {
        Instant now = Instant.now();

        // ---- Vai trò (đã seed ở V2) ----
        Role moderatorRole = requireRole(RoleCode.MODERATOR);
        Role landlordRole = requireRole(RoleCode.LANDLORD);
        Role tenantRole = requireRole(RoleCode.TENANT);

        // ---- Catalog thật (đọc id thật, không đoán) ----
        List<LocationRef> locations = loadLocations();
        if (locations.isEmpty()) {
            throw new IllegalStateException("Chưa seed đơn vị hành chính (provinces/districts/wards) — không thể gieo tin");
        }
        Map<CategoryCode, Long> categoryIds = loadCategoryIds();
        List<Long> amenityIds = amenityRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                .stream().map(Amenity::getId).toList();
        if (categoryIds.isEmpty() || amenityIds.isEmpty()) {
            throw new IllegalStateException("Chưa seed categories/amenities — không thể gieo tin");
        }

        String passwordHash = passwordEncoder.encode(DEMO_PASSWORD);

        // ============================ 1. NGƯỜI DÙNG ============================
        // 1.1 Moderator
        User moderator = createUser("moderator", "Nguyễn Văn Kiểm Duyệt", "0902000001",
                Gender.MALE, passwordHash, now);
        assignRole(moderator, moderatorRole, now);

        // 1.2 Chủ trọ (6) + LandlordProfile; 2 người đầu kiêm luôn vai trò người thuê
        String[] landlordNames = {
                "Trần Thị Hồng Loan", "Phạm Văn Cường", "Lê Thị Mai Anh",
                "Hoàng Minh Đức", "Vũ Thị Thu Hà", "Đặng Quốc Bảo"
        };
        String[] companyNames = {
                "Nhà trọ Hồng Loan", "Cường Phát Homestay", "Mai Anh Apartment",
                null, "Thu Hà Housing", null
        };
        List<User> landlords = new ArrayList<>();
        for (int i = 0; i < landlordNames.length; i++) {
            User u = createUser("landlord" + (i + 1), landlordNames[i],
                    String.format("090300%04d", i + 1),
                    (i % 2 == 0) ? Gender.FEMALE : Gender.MALE, passwordHash, now);
            assignRole(u, landlordRole, now);
            if (i < 2) {
                assignRole(u, tenantRole, now); // vài chủ trọ cũng là người thuê
            }
            boolean verified = (i % 2 == 0); // một nửa đã xác minh
            LandlordProfile profile = LandlordProfile.builder()
                    .user(u)
                    .displayName(companyNames[i] != null ? companyNames[i] : landlordNames[i])
                    .contactName(landlordNames[i])
                    .contactPhone(u.getPhone())
                    .contactEmail(u.getEmail())
                    .companyName(companyNames[i])
                    .address("Số " + (10 + i) + " đường Nguyễn Trãi, " + locations.get(i % locations.size()).label())
                    .allowChat(true)
                    .verificationStatus(verified ? VerificationStatus.VERIFIED : VerificationStatus.PENDING)
                    .verifiedAt(verified ? now.minus(Duration.ofDays(20L + i)) : null)
                    .verifiedBy(verified ? moderator.getId() : null)
                    .verificationNote(verified ? "Đã đối chiếu CCCD và giấy tờ nhà hợp lệ." : null)
                    .trustScore(new BigDecimal(verified ? "95.00" : "80.00"))
                    .build();
            landlordProfileRepository.save(profile);
            landlords.add(u);
        }

        // 1.3 Người thuê (12) + UserProfile
        String[] tenantNames = {
                "Nguyễn Thị Lan", "Trần Văn Nam", "Lê Hoàng Phúc", "Phạm Thị Ngọc",
                "Đỗ Minh Quân", "Bùi Thị Hương", "Ngô Văn Tài", "Dương Thị Kim",
                "Võ Thành Long", "Đinh Thị Thảo", "Cao Văn Sơn", "Lý Thị Bích"
        };
        String[] occupations = {
                "Sinh viên", "Nhân viên văn phòng", "Kỹ sư phần mềm", "Giáo viên",
                "Nhân viên bán hàng", "Kế toán", "Công nhân", "Freelancer",
                "Sinh viên", "Nhân viên marketing", "Tài xế", "Y tá"
        };
        String[] bios = {
                "Sinh viên năm 3, sạch sẽ, giờ giấc ổn định, tìm phòng gần trường.",
                "Đi làm giờ hành chính, không hút thuốc, ưu tiên phòng yên tĩnh.",
                "Làm IT, thường làm việc tại nhà, cần chỗ ở có internet mạnh.",
                "Giáo viên, hiền lành, tìm phòng khép kín an ninh tốt.",
                "Năng động, cởi mở, có thể ở ghép để tiết kiệm chi phí.",
                "Cẩn thận, gọn gàng, ưu tiên khu vực có chỗ để xe.",
                "Chăm chỉ, ít khi ở nhà, cần phòng giá hợp lý.",
                "Làm việc tự do, linh hoạt thời gian, thích không gian thoáng.",
                "Sinh viên tỉnh lẻ, tìm phòng giá rẻ gần bến xe buýt.",
                "Yêu thích sự riêng tư, ưu tiên phòng có ban công.",
                "Đi làm ca, cần khu trọ giờ giấc tự do.",
                "Nhẹ nhàng, sạch sẽ, tìm phòng gần bệnh viện nơi làm việc."
        };
        List<User> tenants = new ArrayList<>();
        for (int i = 0; i < tenantNames.length; i++) {
            User u = createUser("tenant" + (i + 1), tenantNames[i],
                    String.format("090400%04d", i + 1),
                    (i % 2 == 0) ? Gender.FEMALE : Gender.MALE, passwordHash, now);
            assignRole(u, tenantRole, now);
            LocationRef loc = locations.get(i % locations.size());
            UserProfile profile = UserProfile.builder()
                    .user(u)
                    .dateOfBirth(LocalDate.of(1995 + (i % 8), 1 + (i % 12), 1 + (i % 27)))
                    .bio(bios[i])
                    .occupation(occupations[i])
                    .provinceId(loc.provinceId())
                    .districtId(loc.districtId())
                    .addressDetail("Ngõ " + (5 + i) + " đường Trần Phú")
                    .preferredGenderRequirement((i % 3 == 0) ? GenderRequirement.ANY
                            : (i % 3 == 1 ? GenderRequirement.MALE_ONLY : GenderRequirement.FEMALE_ONLY))
                    .build();
            userProfileRepository.save(profile);
            tenants.add(u);
        }

        // ============================ 2. TIN ĐĂNG ============================
        String[] titles = {
                "Phòng trọ khép kín gần Đại học Bách Khoa, có gác lửng",
                "Chung cư mini full nội thất, thang máy, an ninh 24/7",
                "Cho thuê căn hộ 2 phòng ngủ view thoáng mát",
                "Nhà nguyên căn 3 tầng tiện kinh doanh, mặt tiền rộng",
                "Tìm người ở ghép phòng nữ, sạch sẽ, giờ giấc tự do",
                "Phòng trọ giá rẻ cho sinh viên, gần chợ và bến xe",
                "Studio cao cấp trung tâm thành phố, đầy đủ tiện nghi",
                "Phòng trọ có ban công đón nắng, thoáng đãng yên tĩnh",
                "Chung cư mini mới xây, nội thất cơ bản, giá tốt",
                "Căn hộ dịch vụ gần khu công nghiệp, dọn vào ở ngay",
                "Nhà trọ khép kín có chỗ để xe rộng rãi, an ninh",
                "Phòng ở ghép nam gần trung tâm thương mại",
                "Cho thuê phòng master rộng 30m2 có điều hòa",
                "Mặt bằng nhỏ cho thuê làm văn phòng hoặc shop",
                "Phòng trọ mới sơn sửa, wifi tốc độ cao miễn phí",
                "Chung cư mini gần bệnh viện, tiện đi làm ca đêm",
                "Căn hộ 1 phòng ngủ tách bếp, nội thất đầy đủ",
                "Nhà nguyên căn 2 tầng sân vườn yên tĩnh",
                "Phòng trọ tầng trệt tiện cho người lớn tuổi",
                "Tìm bạn nữ ở ghép căn hộ mini view đẹp",
                "Phòng trọ cao cấp có bảo vệ, camera an ninh",
                "Homestay cho thuê theo tháng gần biển",
                "Phòng trọ sinh viên khu yên tĩnh, gần trường",
                "Căn hộ mini tiện nghi cho cặp đôi mới cưới",
                "Nhà trọ giá rẻ khu dân cư đông đúc, an toàn",
                "Phòng ở ghép giá rẻ cho người đi làm",
                "Chung cư mini ban công thoáng, có máy giặt chung",
                "Phòng trọ full nội thất chỉ việc xách vali vào ở"
        };
        CategoryCode[] catCycle = {
                CategoryCode.BOARDING_HOUSE, CategoryCode.MINI_APARTMENT, CategoryCode.APARTMENT,
                CategoryCode.WHOLE_HOUSE, CategoryCode.ROOMMATE, CategoryCode.BOARDING_HOUSE,
                CategoryCode.MINI_APARTMENT, CategoryCode.BOARDING_HOUSE, CategoryCode.MINI_APARTMENT,
                CategoryCode.APARTMENT, CategoryCode.BOARDING_HOUSE, CategoryCode.ROOMMATE,
                CategoryCode.APARTMENT, CategoryCode.SMALL_PREMISES, CategoryCode.BOARDING_HOUSE,
                CategoryCode.MINI_APARTMENT, CategoryCode.APARTMENT, CategoryCode.WHOLE_HOUSE,
                CategoryCode.BOARDING_HOUSE, CategoryCode.ROOMMATE, CategoryCode.BOARDING_HOUSE,
                CategoryCode.HOMESTAY, CategoryCode.BOARDING_HOUSE, CategoryCode.MINI_APARTMENT,
                CategoryCode.BOARDING_HOUSE, CategoryCode.ROOMMATE, CategoryCode.MINI_APARTMENT,
                CategoryCode.BOARDING_HOUSE
        };

        List<Listing> listings = new ArrayList<>();
        for (int i = 0; i < titles.length; i++) {
            LocationRef loc = locations.get(i % locations.size());
            CategoryCode code = catCycle[i % catCycle.length];
            Long categoryId = categoryIds.getOrDefault(code, categoryIds.values().iterator().next());
            User owner = landlords.get(i % landlords.size());
            ListingStatus status = statusForIndex(i);

            BigDecimal price = BigDecimal.valueOf(1_500_000L + (long) (i % 14) * 450_000L);
            BigDecimal area = BigDecimal.valueOf(15L + (long) (i % 10) * 4L);
            int maxOccupants = 2 + (i % 3);
            int curOccupants = 1 + (i % 2);

            Instant publishedAt = (status == ListingStatus.PENDING || status == ListingStatus.DRAFT)
                    ? null : now.minus(Duration.ofDays(1L + (long) i * 3));
            Instant expiredAt = (status == ListingStatus.EXPIRED)
                    ? now.minus(Duration.ofDays(2))
                    : now.plus(Duration.ofDays(30));

            Listing listing = Listing.builder()
                    .ownerId(owner.getId())
                    .categoryId(categoryId)
                    .title(titles[i])
                    .slug(slugify(titles[i]) + "-" + (i + 1))
                    .description(buildDescription(titles[i], area, price, loc))
                    .price(price)
                    .area(area)
                    .depositAmount(price)
                    .electricityPrice(new BigDecimal("3500"))
                    .waterPrice(new BigDecimal("100000"))
                    .provinceId(loc.provinceId())
                    .districtId(loc.districtId())
                    .wardId(loc.wardId())
                    .addressDetail("Số " + (12 + i) + ", ngõ " + (3 + i % 20) + " đường Nguyễn Văn Cừ, " + loc.label())
                    .roomCount(1)
                    .toiletCount(1)
                    .toiletType((i % 3 == 0) ? ToiletType.SHARED : ToiletType.PRIVATE)
                    .maxOccupants(maxOccupants)
                    .currentOccupants(curOccupants)
                    .genderRequirement(switch (i % 3) {
                        case 0 -> GenderRequirement.ANY;
                        case 1 -> GenderRequirement.MALE_ONLY;
                        default -> GenderRequirement.FEMALE_ONLY;
                    })
                    .petAllowed(i % 4 == 0)
                    .parkingAvailable(i % 2 == 0)
                    .curfewType((i % 2 == 0) ? CurfewType.FREE : (i % 4 == 1 ? CurfewType.CURFEW : CurfewType.UNKNOWN))
                    .furnitureStatus(switch (i % 3) {
                        case 0 -> FurnitureStatus.FULL;
                        case 1 -> FurnitureStatus.BASIC;
                        default -> FurnitureStatus.NONE;
                    })
                    .availableFrom(LocalDate.now().plusDays(i % 10))
                    .status(status)
                    .publishedAt(publishedAt)
                    .expiredAt(expiredAt)
                    .build();
            listing = listingRepository.save(listing);
            listings.add(listing);

            // ---- Ảnh JPEG thật (1-3 ảnh mỗi tin) ----
            int imageCount = 1 + (i % 3);
            createImages(listing, imageCount, i);

            // ---- Tiện ích (3-6 mỗi tin, không trùng) ----
            int amCount = Math.min(3 + (i % 4), amenityIds.size());
            int start = (i * 2) % amenityIds.size();
            for (int k = 0; k < amCount; k++) {
                Long amenityId = amenityIds.get((start + k) % amenityIds.size());
                listingAmenityRepository.save(ListingAmenity.builder()
                        .listing(listing)
                        .amenityId(amenityId)
                        .build());
            }
        }

        // ---- Cụm tin "Phòng trọ" dày (ACTIVE) để AI dự đoán giá đủ mẫu ----
        // Ước lượng giá cần >= AI_PRICE_MIN_SAMPLES tin tương đương (cùng loại, diện tích
        // gần nhau, đang hiển thị). Thị trường thật ở Hà Nội có mật độ phòng trọ cao, nên
        // ta gieo một khu trọ tập trung (đủ mẫu ở phạm vi PHƯỜNG) + rải thêm toàn thành phố.
        Long boardingCatId = categoryIds.get(CategoryCode.BOARDING_HOUSE);
        if (boardingCatId != null && !locations.isEmpty()) {
            // Phường trọng điểm: ưu tiên Cầu Giấy (khu trọ sinh viên điển hình), nếu không có
            // thì lấy phường đầu tiên. Dồn 10 tin cùng phường -> đủ mẫu ở phạm vi PHƯỜNG.
            LocationRef hotWard = locations.stream()
                    .filter(l -> l.label().contains("Cầu Giấy"))
                    .findFirst()
                    .orElse(locations.get(0));
            String[] clusterAdj = {"khép kín", "mới xây", "full nội thất", "thoáng mát",
                    "giá tốt", "an ninh 24/7", "gần trường", "tiện đi làm", "có gác lửng", "ban công rộng"};
            // Diện tích SÁT nhau (20-26m2) để luôn nằm trong dải dung sai diện tích khi tra cứu.
            int[] clusterAreas = {20, 22, 24, 26, 22, 24, 20, 26, 24, 22, 21, 25, 23, 20};
            int clusterSize = 24;
            for (int c = 0; c < clusterSize; c++) {
                int gi = titles.length + c;
                boolean hot = c < 10;                       // 10 tin đầu dồn vào phường trọng điểm
                LocationRef loc = hot ? hotWard : locations.get(gi % locations.size());
                User owner = landlords.get(gi % landlords.size());
                int areaInt = clusterAreas[c % clusterAreas.length];
                BigDecimal area = BigDecimal.valueOf(areaInt);
                BigDecimal price = BigDecimal.valueOf(2_200_000L + (long) (c % 8) * 300_000L); // 2.2tr..4.3tr
                String district = loc.label().contains(",")
                        ? loc.label().split(",")[1].trim() : loc.label();
                String title = "Phòng trọ " + clusterAdj[c % clusterAdj.length]
                        + " " + areaInt + "m2 " + district;

                Listing listing = Listing.builder()
                        .ownerId(owner.getId())
                        .categoryId(boardingCatId)
                        .title(title)
                        .slug(slugify(title) + "-" + (gi + 1))
                        .description(buildDescription(title, area, price, loc))
                        .price(price)
                        .area(area)
                        .depositAmount(price)
                        .electricityPrice(new BigDecimal("3500"))
                        .waterPrice(new BigDecimal("100000"))
                        .provinceId(loc.provinceId())
                        .districtId(loc.districtId())
                        .wardId(loc.wardId())
                        .addressDetail("Số " + (2 + c) + ", ngõ " + (10 + c % 30) + " " + district)
                        .roomCount(1)
                        .toiletCount(1)
                        .toiletType((c % 4 == 0) ? ToiletType.SHARED : ToiletType.PRIVATE)
                        .maxOccupants(2 + (c % 2))
                        .currentOccupants(1)
                        .genderRequirement(GenderRequirement.ANY)
                        .petAllowed(c % 5 == 0)
                        .parkingAvailable(c % 2 == 0)
                        .curfewType((c % 2 == 0) ? CurfewType.FREE : CurfewType.CURFEW)
                        .furnitureStatus(switch (c % 3) {
                            case 0 -> FurnitureStatus.FULL;
                            case 1 -> FurnitureStatus.BASIC;
                            default -> FurnitureStatus.NONE;
                        })
                        .availableFrom(LocalDate.now().plusDays(c % 7))
                        .status(ListingStatus.ACTIVE)
                        .publishedAt(now.minus(Duration.ofDays(1L + c)))
                        .expiredAt(now.plus(Duration.ofDays(30)))
                        .build();
                listing = listingRepository.save(listing);
                listings.add(listing);

                createImages(listing, 1 + (c % 2), gi);

                int amCount = Math.min(3 + (c % 3), amenityIds.size());
                int start = (gi * 2) % amenityIds.size();
                for (int k = 0; k < amCount; k++) {
                    listingAmenityRepository.save(ListingAmenity.builder()
                            .listing(listing)
                            .amenityId(amenityIds.get((start + k) % amenityIds.size()))
                            .build());
                }
            }
        }

        // ============================ 3. TƯƠNG TÁC ============================
        // Bộ đếm tích lũy theo listingId để cập nhật denormalize sau.
        Map<Long, int[]> counters = new HashMap<>(); // [views, favorites, comments, pos, neg, reviewCount, reviewSum]
        for (Listing l : listings) {
            counters.put(l.getId(), new int[7]);
        }

        // 3.1 FAVORITE (~40, không trùng cặp)
        Set<String> favKeys = new HashSet<>();
        int favCreated = 0;
        for (int k = 0; k < 90 && favCreated < 40; k++) {
            User tenant = tenants.get(k % tenants.size());
            Listing listing = listings.get((k * 3 + k / tenants.size()) % listings.size());
            String key = tenant.getId() + ":" + listing.getId();
            if (!favKeys.add(key)) {
                continue;
            }
            favoriteRepository.save(Favorite.builder()
                    .userId(tenant.getId())
                    .listingId(listing.getId())
                    .note(k % 5 == 0 ? "Ưng phòng này, chờ đi xem thực tế." : null)
                    .build());
            counters.get(listing.getId())[1]++;
            favCreated++;
        }

        // 3.2 VIEW_HISTORY (~30)
        int viewCreated = 0;
        for (int k = 0; k < 30; k++) {
            User tenant = tenants.get(k % tenants.size());
            Listing listing = listings.get((k * 5 + 1) % listings.size());
            viewHistoryRepository.save(ViewHistory.builder()
                    .listingId(listing.getId())
                    .userId(tenant.getId())
                    .sessionId(UUID.randomUUID().toString())
                    .ipAddress("113.161." + (k % 250) + "." + ((k * 7) % 250))
                    .userAgent("Mozilla/5.0 (demo-seed)")
                    .isCounted(true)
                    .build());
            counters.get(listing.getId())[0]++;
            viewCreated++;
        }

        // 3.3 COMMENT (~30, đa cảm xúc, có sẵn nhãn/điểm cho trang AI)
        String[] posComments = {
                "Phòng đẹp, chủ nhà thân thiện, mình đã ở 6 tháng rất hài lòng.",
                "Khu vực an ninh, sạch sẽ, giá cả hợp lý, rất đáng để thuê.",
                "Chủ trọ nhiệt tình, sửa chữa nhanh khi có hư hỏng. Recommend!",
                "Vị trí thuận tiện, gần trường và chợ, đi lại rất dễ dàng.",
                "Nội thất mới, wifi mạnh, ở rất thoải mái và yên tĩnh."
        };
        String[] negComments = {
                "Phòng ẩm thấp, tường bị thấm nước, không như hình đăng.",
                "Chủ nhà khó tính, hay bắt bẻ, giá điện nước cao vô lý.",
                "An ninh kém, hay mất đồ, mình đã phải chuyển đi sớm.",
                "Phòng nhỏ hơn quảng cáo nhiều, wifi thì chập chờn liên tục.",
                "Hàng xóm ồn ào, ban đêm rất khó ngủ, không nên thuê."
        };
        String[] neuComments = {
                "Cho hỏi phòng còn trống không ạ, mình muốn xem vào cuối tuần?",
                "Giá này đã bao gồm điện nước chưa vậy chủ nhà?",
                "Phòng có cho nuôi thú cưng nhỏ không ạ?",
                "Khu này để xe máy có thu thêm phí không nhỉ?",
                "Cho mình xin số điện thoại để liên hệ xem phòng với ạ."
        };
        int commentCreated = 0;
        for (int k = 0; k < 30; k++) {
            Listing listing = listings.get((k * 7 + 2) % listings.size());
            User tenant = tenants.get(k % tenants.size());
            int mood = k % 5; // 0,1 tích cực; 2,3 tiêu cực/trung tính; 4 chờ phân tích
            String content;
            SentimentLabel label;
            BigDecimal score;
            BigDecimal confidence;
            if (mood == 0 || mood == 1) {
                content = posComments[k % posComments.length];
                label = SentimentLabel.POSITIVE;
                score = new BigDecimal("0.720");
                confidence = new BigDecimal("0.910");
            } else if (mood == 2) {
                content = negComments[k % negComments.length];
                label = SentimentLabel.NEGATIVE;
                score = new BigDecimal("-0.650");
                confidence = new BigDecimal("0.880");
            } else if (mood == 3) {
                content = neuComments[k % neuComments.length];
                label = SentimentLabel.NEUTRAL;
                score = new BigDecimal("0.050");
                confidence = new BigDecimal("0.700");
            } else {
                content = neuComments[(k + 2) % neuComments.length];
                label = SentimentLabel.PENDING_ANALYSIS; // để demo hàng chờ phân tích AI
                score = null;
                confidence = null;
            }
            commentRepository.save(Comment.builder()
                    .listingId(listing.getId())
                    .userId(tenant.getId())
                    .content(content)
                    .status(CommentStatus.VISIBLE)
                    .sentimentLabel(label)
                    .sentimentScore(score)
                    .sentimentConfidence(confidence)
                    .isRiskComment(label == SentimentLabel.NEGATIVE && k % 10 == 2)
                    .build());
            int[] c = counters.get(listing.getId());
            c[2]++;
            if (label == SentimentLabel.POSITIVE) {
                c[3]++;
            } else if (label == SentimentLabel.NEGATIVE) {
                c[4]++;
            }
            commentCreated++;
        }

        // 3.4 REVIEW (~20, rating 1-5, không trùng cặp) -> cập nhật rating tin + chủ trọ
        String[] reviewTexts = {
                "Ở ổn, đúng như mô tả, sẽ giới thiệu cho bạn bè.",
                "Rất hài lòng, chủ nhà tốt bụng, phòng sạch sẽ thoáng mát.",
                "Tạm được, hơi ồn nhưng giá rẻ nên chấp nhận được.",
                "Không hài lòng, phòng xuống cấp và ẩm mốc nhiều.",
                "Quá tệ, chủ nhà giữ tiền cọc vô lý khi trả phòng."
        };
        int[] ratingCycle = {5, 4, 5, 3, 2, 4, 5, 1, 4, 5, 3, 4};
        Set<String> reviewKeys = new HashSet<>();
        Map<Long, int[]> landlordRating = new HashMap<>(); // ownerId -> [count, sum]
        int reviewCreated = 0;
        for (int k = 0; k < 60 && reviewCreated < 20; k++) {
            Listing listing = listings.get((k * 11 + 3) % listings.size());
            if (listing.getStatus() == ListingStatus.PENDING || listing.getStatus() == ListingStatus.DRAFT) {
                continue;
            }
            User tenant = tenants.get(k % tenants.size());
            if (tenant.getId().equals(listing.getOwnerId())) {
                continue;
            }
            String key = tenant.getId() + ":" + listing.getId();
            if (!reviewKeys.add(key)) {
                continue;
            }
            int rating = ratingCycle[k % ratingCycle.length];
            String content = rating >= 4 ? reviewTexts[k % 2] : reviewTexts[2 + (k % 3)];
            reviewRepository.save(Review.builder()
                    .listingId(listing.getId())
                    .userId(tenant.getId())
                    .landlordId(listing.getOwnerId())
                    .rating(rating)
                    .content(content)
                    .status(ReviewStatus.VISIBLE)
                    .isVerifiedContact(k % 2 == 0)
                    .build());
            int[] c = counters.get(listing.getId());
            c[5]++;
            c[6] += rating;
            landlordRating.computeIfAbsent(listing.getOwnerId(), x -> new int[2]);
            landlordRating.get(listing.getOwnerId())[0]++;
            landlordRating.get(listing.getOwnerId())[1] += rating;
            reviewCreated++;
        }

        // ---- Cập nhật bộ đếm denormalize của từng tin ----
        for (Listing l : listings) {
            int[] c = counters.get(l.getId());
            int idx = listings.indexOf(l);
            l.setViewCount(c[0] + 5 + (idx % 9) * 7);        // thêm nền nhẹ theo index
            l.setFavoriteCount(c[1]);
            l.setCommentCount(c[2]);
            l.setPositiveCommentCount(c[3]);
            l.setNegativeCommentCount(c[4]);
            l.setReviewCount(c[5]);
            if (c[5] > 0) {
                l.setAverageRating(BigDecimal.valueOf(c[6])
                        .divide(BigDecimal.valueOf(c[5]), 2, RoundingMode.HALF_UP));
            }
            listingRepository.save(l);
        }

        // ---- Cập nhật hồ sơ chủ trọ: tổng tin + đánh giá trung bình ----
        updateLandlordAggregates(landlords, listings, landlordRating);

        // ============================ 4. THEO DÕI (~15) ============================
        Set<String> followKeys = new HashSet<>();
        int followCreated = 0;
        for (int k = 0; k < 60 && followCreated < 15; k++) {
            User follower = tenants.get(k % tenants.size());
            User landlord = landlords.get((k * 3 + 1) % landlords.size());
            if (follower.getId().equals(landlord.getId())) {
                continue;
            }
            String key = follower.getId() + ":" + landlord.getId();
            if (!followKeys.add(key)) {
                continue;
            }
            followRepository.save(Follow.builder()
                    .follower(follower)
                    .landlord(landlord)
                    .notifyNewListing(k % 3 != 0)
                    .build());
            followCreated++;
        }

        // ============================ 5. BÁO CÁO (~5) ============================
        int reportCreated = createReports(listings, tenants, moderator, now);

        // ============================ 6. THÔNG BÁO (~10) ============================
        int notiCreated = createNotifications(listings, landlords, tenants, now);

        // ============================ 7. THANH TOÁN + ĐẨY TIN (~5) ============================
        int paymentCreated = createPaymentsAndPromotions(landlords, listings, now);

        // ============================ TỔNG KẾT ============================
        log.info("Gieo dữ liệu mẫu HOÀN TẤT: {} người dùng (1 moderator + {} chủ trọ + {} người thuê), "
                        + "{} tin đăng, {} lưu tin, {} lượt xem, {} bình luận, {} đánh giá, {} theo dõi, "
                        + "{} báo cáo, {} thông báo, {} thanh toán+đẩy tin",
                1 + landlords.size() + tenants.size(), landlords.size(), tenants.size(),
                listings.size(), favCreated, viewCreated, commentCreated, reviewCreated,
                followCreated, reportCreated, notiCreated, paymentCreated);
    }

    // =====================================================================================
    //  Người dùng
    // =====================================================================================

    private User createUser(String localPart, String fullName, String phone, Gender gender,
                            String passwordHash, Instant now) {
        User user = User.builder()
                .fullName(fullName)
                .email(localPart + DOMAIN)
                .phone(phone)
                .passwordHash(passwordHash)
                .gender(gender)
                .status(UserStatus.ACTIVE)
                .emailVerifiedAt(now)
                .failedLoginCount(0)
                .build();
        return userRepository.save(user);
    }

    private void assignRole(User user, Role role, Instant now) {
        userRoleRepository.save(UserRole.builder()
                .user(user)
                .role(role)
                .assignedAt(now)
                .build());
    }

    private Role requireRole(String code) {
        return roleRepository.findByCodeAndDeletedAtIsNull(code)
                .orElseThrow(() -> new IllegalStateException("Chưa seed vai trò " + code + " (kiểm tra V2 migration)"));
    }

    private void updateLandlordAggregates(List<User> landlords, List<Listing> listings,
                                          Map<Long, int[]> landlordRating) {
        for (User landlord : landlords) {
            long total = listings.stream().filter(l -> l.getOwnerId().equals(landlord.getId())).count();
            long active = listings.stream()
                    .filter(l -> l.getOwnerId().equals(landlord.getId())
                            && l.getStatus() == ListingStatus.ACTIVE)
                    .count();
            Optional<LandlordProfile> opt = landlordProfileRepository.findAll().stream()
                    .filter(p -> p.getUser().getId().equals(landlord.getId()))
                    .findFirst();
            if (opt.isEmpty()) {
                continue;
            }
            LandlordProfile profile = opt.get();
            profile.setTotalListings((int) total);
            profile.setTotalActiveListings((int) active);
            int[] r = landlordRating.get(landlord.getId());
            if (r != null && r[0] > 0) {
                profile.setReviewCount(r[0]);
                profile.setAverageRating(BigDecimal.valueOf(r[1])
                        .divide(BigDecimal.valueOf(r[0]), 2, RoundingMode.HALF_UP));
            }
            landlordProfileRepository.save(profile);
        }
    }

    // =====================================================================================
    //  Báo cáo / Thông báo / Thanh toán
    // =====================================================================================

    private int createReports(List<Listing> listings, List<User> tenants, User moderator, Instant now) {
        ReportReason[] reasons = {
                ReportReason.WRONG_INFO, ReportReason.SCAM, ReportReason.ALREADY_RENTED,
                ReportReason.FAKE_IMAGE, ReportReason.WRONG_PRICE
        };
        ReportSeverity[] severities = {
                ReportSeverity.LOW, ReportSeverity.HIGH, ReportSeverity.MEDIUM,
                ReportSeverity.MEDIUM, ReportSeverity.HIGH
        };
        String[] descriptions = {
                "Thông tin diện tích không đúng thực tế khi đi xem phòng.",
                "Nghi ngờ lừa đảo, yêu cầu chuyển cọc trước khi cho xem phòng.",
                "Tin này đã cho thuê từ tuần trước nhưng vẫn còn hiển thị.",
                "Ảnh đăng là ảnh mạng, không phải phòng thật.",
                "Giá đăng thấp hơn nhiều so với giá báo khi liên hệ."
        };
        int created = 0;
        for (int k = 0; k < reasons.length; k++) {
            Listing listing = listings.get((k * 4 + 1) % listings.size());
            User reporter = tenants.get(k % tenants.size());
            boolean resolved = (k % 2 == 1);
            reportRepository.save(Report.builder()
                    .reporterId(reporter.getId())
                    .targetType(ReportTargetType.LISTING)
                    .targetId(listing.getId())
                    .listingId(listing.getId())
                    .reason(reasons[k])
                    .description(descriptions[k])
                    .status(resolved ? ReportStatus.RESOLVED : ReportStatus.PENDING)
                    .severity(severities[k])
                    .isValid(resolved ? (k % 4 == 1) : null)
                    .resolutionNote(resolved ? "Đã kiểm tra và xử lý theo quy định." : null)
                    .resolvedBy(resolved ? moderator.getId() : null)
                    .resolvedAt(resolved ? now.minus(Duration.ofDays(k)) : null)
                    .dedupKey("LISTING:" + listing.getId() + ":" + reasons[k].name())
                    .build());
            created++;
        }
        return created;
    }

    private int createNotifications(List<Listing> listings, List<User> landlords,
                                    List<User> tenants, Instant now) {
        NotificationType[] types = {
                NotificationType.LISTING_APPROVED, NotificationType.NEW_COMMENT,
                NotificationType.NEW_REVIEW, NotificationType.LISTING_EXPIRING,
                NotificationType.PAYMENT_SUCCESS, NotificationType.NEW_MATCHING_LISTING,
                NotificationType.FOLLOWED_LANDLORD_NEW_LISTING, NotificationType.REPORT_THRESHOLD,
                NotificationType.AI_NEGATIVE_ALERT, NotificationType.ACCOUNT_VERIFICATION
        };
        String[] titles = {
                "Tin của bạn đã được duyệt",
                "Có bình luận mới trên tin của bạn",
                "Tin của bạn vừa nhận đánh giá mới",
                "Tin của bạn sắp hết hạn",
                "Thanh toán đẩy tin thành công",
                "Có tin mới phù hợp với nhu cầu của bạn",
                "Chủ trọ bạn theo dõi vừa đăng tin mới",
                "Một tin đăng bị báo cáo nhiều lần",
                "Cảnh báo: tin có nhiều bình luận tiêu cực",
                "Hồ sơ chủ trọ của bạn đã được xác minh"
        };
        String[] contents = {
                "Tin đăng của bạn đã được kiểm duyệt và đang hiển thị công khai.",
                "Một người dùng vừa để lại bình luận, hãy phản hồi để tăng uy tín.",
                "Người thuê vừa đánh giá tin của bạn, xem chi tiết ngay.",
                "Tin của bạn sẽ hết hạn trong 3 ngày tới, gia hạn để tiếp tục hiển thị.",
                "Giao dịch đẩy tin của bạn đã hoàn tất thành công.",
                "Chúng tôi tìm thấy một tin đăng phù hợp với bộ lọc bạn đã lưu.",
                "Chủ trọ bạn đang theo dõi vừa đăng một tin mới, xem ngay.",
                "Hệ thống ghi nhận nhiều báo cáo cho một tin đăng, vui lòng kiểm tra.",
                "Tin có tỷ lệ bình luận tiêu cực cao, nên rà soát nội dung.",
                "Hồ sơ chủ trọ của bạn đã được xác minh thành công."
        };
        int created = 0;
        for (int k = 0; k < types.length; k++) {
            // Xen kẽ người nhận giữa chủ trọ và người thuê cho đa dạng.
            User recipient = (k % 2 == 0)
                    ? landlords.get(k % landlords.size())
                    : tenants.get(k % tenants.size());
            Listing listing = listings.get((k * 3) % listings.size());
            boolean read = (k % 3 == 0);
            notificationRepository.save(Notification.builder()
                    .userId(recipient.getId())
                    .type(types[k])
                    .channel(NotificationChannel.IN_APP)
                    .title(titles[k])
                    .content(contents[k])
                    .link("/tin/" + listing.getSlug())
                    .refType("LISTING")
                    .refId(listing.getId())
                    .isRead(read)
                    .readAt(read ? now.minus(Duration.ofHours(k + 1L)) : null)
                    .build());
            created++;
        }
        return created;
    }

    private int createPaymentsAndPromotions(List<User> landlords, List<Listing> listings, Instant now) {
        PromotionPackage pkg = promotionPackageRepository.findByCodeAndDeletedAtIsNull("PUSH_TOP_30")
                .or(() -> promotionPackageRepository.findAll().stream().findFirst())
                .orElse(null);
        if (pkg == null) {
            log.warn("Chưa seed gói đẩy tin (promotion_packages) — bỏ qua phần thanh toán/đẩy tin demo");
            return 0;
        }
        // Chỉ đẩy các tin ACTIVE để demo tin nổi bật hợp lý.
        List<Listing> activeListings = listings.stream()
                .filter(l -> l.getStatus() == ListingStatus.ACTIVE)
                .limit(5)
                .toList();
        int created = 0;
        for (int k = 0; k < activeListings.size(); k++) {
            Listing listing = activeListings.get(k);
            Long ownerId = listing.getOwnerId();
            BigDecimal amount = pkg.getPrice();
            Payment payment = Payment.builder()
                    .userId(ownerId)
                    .listingId(listing.getId())
                    .packageId(pkg.getId())
                    .amount(amount)
                    .discountAmount(BigDecimal.ZERO)
                    .finalAmount(amount)
                    .currency("VND")
                    .paymentMethod(PaymentMethod.SANDBOX)
                    .transactionCode("DEMO-PAY-" + String.format("%05d", k + 1))
                    .status(PaymentStatus.SUCCESS)
                    .paidAt(now.minus(Duration.ofDays(k + 1L)))
                    .expiresAt(now.plus(Duration.ofDays(30)))
                    .build();
            payment = paymentRepository.save(payment);

            Instant startAt = now.minus(Duration.ofDays(k + 1L));
            Instant endAt = startAt.plus(Duration.ofDays(pkg.getDurationDays().longValue()));
            promotionSubscriptionRepository.save(PromotionSubscription.builder()
                    .paymentId(payment.getId())
                    .listingId(listing.getId())
                    .packageId(pkg.getId())
                    .userId(ownerId)
                    .priority(pkg.getPriority())
                    .status(SubscriptionStatus.ACTIVE)
                    .startAt(startAt)
                    .endAt(endAt)
                    .build());

            // Đồng bộ cờ đẩy tin trên listing.
            listing.setIsPromoted(true);
            listing.setPromotedUntil(endAt);
            listing.setPromotionPriority(pkg.getPriority());
            listingRepository.save(listing);
            created++;
        }
        return created;
    }

    // =====================================================================================
    //  Ảnh JPEG thật
    // =====================================================================================

    private void createImages(Listing listing, int count, int listingIndex) {
        Path root = Paths.get(storageProperties.getRoot()).toAbsolutePath().normalize();
        String publicBase = storageProperties.getPublicBaseUrl().replaceAll("/+$", "");
        Color[] palette = {
                new Color(37, 99, 235), new Color(22, 163, 74), new Color(217, 119, 6),
                new Color(190, 24, 93), new Color(109, 40, 217), new Color(13, 148, 136)
        };
        String label = stripDiacritics(listing.getTitle()).toUpperCase(Locale.ROOT);
        for (int j = 0; j < count; j++) {
            try {
                Color bg = palette[(listingIndex + j) % palette.length];
                String uuid = UUID.randomUUID().toString().replace("-", "");
                String key = "listings/" + listing.getId() + "/" + uuid;
                Path dir = root.resolve("listings").resolve(String.valueOf(listing.getId()));
                Files.createDirectories(dir);

                String caption = label + "  #" + (j + 1);
                BufferedImage original = drawImage(1200, 800, bg, caption);
                BufferedImage medium = drawImage(800, 533, bg, caption);
                BufferedImage thumb = drawImage(400, 267, bg, caption);

                Path originalFile = dir.resolve(uuid + ".jpg");
                Path mediumFile = dir.resolve(uuid + "_medium.jpg");
                Path thumbFile = dir.resolve(uuid + "_thumb.jpg");
                ImageIO.write(original, "jpg", originalFile.toFile());
                ImageIO.write(medium, "jpg", mediumFile.toFile());
                ImageIO.write(thumb, "jpg", thumbFile.toFile());

                int fileSize = (int) Math.min(Files.size(originalFile), 5_242_880L);
                if (fileSize <= 0) {
                    fileSize = 1;
                }
                String url = publicBase + "/" + key;
                listingImageRepository.save(ListingImage.builder()
                        .listing(listing)
                        .url(url)
                        .thumbnailUrl(url + "_thumb")
                        .isPrimary(j == 0)
                        .displayOrder(j)
                        .originalName(stripDiacritics(listing.getTitle()) + "-" + (j + 1) + ".jpg")
                        .fileSize(fileSize)
                        .contentType("image/jpeg")
                        .width(1200)
                        .height(800)
                        .build());
            } catch (IOException e) {
                // Ảnh là phần trang trí; lỗi ghi một ảnh không nên làm hỏng cả giao dịch seed.
                log.warn("Không ghi được ảnh mẫu cho tin {} (ảnh #{}): {}",
                        listing.getId(), j + 1, e.getMessage());
            }
        }
    }

    private BufferedImage drawImage(int w, int h, Color bg, String caption) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        // Nền gradient nhẹ theo màu chủ đạo.
        g.setColor(bg);
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 40));
        g.fillRect(0, h * 2 / 3, w, h / 3);
        // Chữ tiêu đề (không dấu) chia dòng.
        g.setColor(Color.WHITE);
        int fontSize = Math.max(14, w / 26);
        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        List<String> lines = wrap(caption, Math.max(12, w / (fontSize / 2 + 6)));
        int y = h / 2 - (lines.size() * fontSize) / 2;
        for (String line : lines) {
            int textW = g.getFontMetrics().stringWidth(line);
            g.drawString(line, Math.max(10, (w - textW) / 2), y);
            y += fontSize + 6;
        }
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, w / 44)));
        g.drawString("WEBTRO DEMO", 16, h - 16);
        g.dispose();
        return img;
    }

    private List<String> wrap(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            if (current.length() + word.length() + 1 > maxChars && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
            if (lines.size() >= 4) {
                break;
            }
        }
        if (current.length() > 0 && lines.size() < 5) {
            lines.add(current.toString());
        }
        return lines;
    }

    // =====================================================================================
    //  Catalog helpers
    // =====================================================================================

    /** Tổ hợp (tỉnh, quận, phường) THẬT từ catalog để gán địa chỉ FK hợp lệ cho tin/hồ sơ. */
    private List<LocationRef> loadLocations() {
        List<LocationRef> result = new ArrayList<>();
        List<Province> provinces = provinceRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        for (Province province : provinces) {
            List<District> districts =
                    districtRepository.findByProvince_IdAndIsActiveTrueOrderByDisplayOrderAsc(province.getId());
            for (District district : districts) {
                List<Ward> wards =
                        wardRepository.findByDistrict_IdAndIsActiveTrueOrderByNameAsc(district.getId());
                if (wards.isEmpty()) {
                    continue;
                }
                // Lấy tối đa 2 phường mỗi quận để có đủ tổ hợp mà không quá tải.
                int take = Math.min(2, wards.size());
                for (int i = 0; i < take; i++) {
                    Ward ward = wards.get(i);
                    result.add(new LocationRef(province.getId(), district.getId(), ward.getId(),
                            ward.getName() + ", " + district.getName() + ", " + province.getShortName()));
                }
            }
        }
        return result;
    }

    private Map<CategoryCode, Long> loadCategoryIds() {
        Map<CategoryCode, Long> map = new EnumMap<>(CategoryCode.class);
        for (Category c : categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc()) {
            map.put(c.getCode(), c.getId());
        }
        return map;
    }

    // =====================================================================================
    //  Thuần logic
    // =====================================================================================

    /** Phân bổ trạng thái: phần lớn ACTIVE, xen kẽ PENDING/HIDDEN/EXPIRED/NEED_REVIEW để demo quản trị. */
    private ListingStatus statusForIndex(int i) {
        int m = i % 13;
        return switch (m) {
            case 5 -> ListingStatus.PENDING;
            case 7 -> ListingStatus.HIDDEN;
            case 9 -> ListingStatus.EXPIRED;
            case 11 -> ListingStatus.NEED_REVIEW;
            default -> ListingStatus.ACTIVE;
        };
    }

    private String buildDescription(String title, BigDecimal area, BigDecimal price, LocationRef loc) {
        return title + ".\n\n"
                + "Diện tích khoảng " + area.toBigInteger() + "m2, giá thuê "
                + formatVnd(price) + "/tháng (chưa gồm điện nước).\n"
                + "Địa chỉ: " + loc.label() + ".\n\n"
                + "Phòng thoáng mát, sạch sẽ, an ninh đảm bảo, khu dân cư văn minh. "
                + "Gần chợ, siêu thị, trường học và bến xe buýt, thuận tiện đi lại. "
                + "Có chỗ để xe, wifi tốc độ cao, giờ giấc thoải mái. "
                + "Liên hệ chủ nhà để được xem phòng và tư vấn chi tiết. "
                + "Ưu tiên khách thuê ở lâu dài, sạch sẽ và có ý thức giữ gìn.";
    }

    private String formatVnd(BigDecimal amount) {
        return String.format(Locale.US, "%,d", amount.toBigInteger()) + "đ";
    }

    /** Bỏ dấu tiếng Việt để đặt slug và vẽ chữ lên ảnh (font mặc định không cần dấu). */
    private String stripDiacritics(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.replace('đ', 'd').replace('Đ', 'D');
    }

    private String slugify(String input) {
        String base = stripDiacritics(input).toLowerCase(Locale.ROOT);
        base = base.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return base.length() > 150 ? base.substring(0, 150) : base;
    }

    /** Tổ hợp địa chỉ hành chính thật (id + nhãn hiển thị). */
    private record LocationRef(Long provinceId, Long districtId, Long wardId, String label) {
    }
}
