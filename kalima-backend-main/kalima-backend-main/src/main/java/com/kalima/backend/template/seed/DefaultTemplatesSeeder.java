package com.kalima.backend.template.seed;

import com.kalima.backend.domain.entity.Company;
import com.kalima.backend.domain.entity.DomainEnums;
import com.kalima.backend.domain.repository.CompanyRepository;
import com.kalima.backend.domain.tenant.TenantContext;
import com.kalima.backend.template.entity.Template;
import com.kalima.backend.template.repository.TemplateRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * DEV-only seeder for the default Arabic fillable-form templates.
 *
 * <p>Mirrors {@link com.kalima.backend.auth.seed.AdminSeeder}: runs once at
 * startup, scoped to {@link Company#DEFAULT_ID}, fully idempotent — a default
 * template is only created when no default of the same
 * {@link DomainEnums.TemplateType} already exists for the tenant.
 *
 * <p>Filter handling: the {@code TenantFilterAspect} only wraps service-layer
 * methods, not seeders, so this class enables the Hibernate {@code tenantFilter}
 * directly on the active Session before reading. That is the same mechanism
 * the aspect uses internally, kept local to avoid touching shared code.
 *
 * <p>Templates are intentionally plain Arabic text with the four supported
 * placeholders ({@code {{company.name}}}, {@code {{client.name}}},
 * {@code {{case.number}}}, {@code {{today}}}); export to PDF / Word can layer
 * on top later without changing the seeded content.
 */
@Component
@Profile("dev")
public class DefaultTemplatesSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultTemplatesSeeder.class);
    private static final String FILTER_NAME = "tenantFilter";
    private static final String FILTER_PARAM = "companyId";

    private final TemplateRepository templateRepository;
    private final CompanyRepository companyRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public DefaultTemplatesSeeder(TemplateRepository templateRepository,
                                  CompanyRepository companyRepository) {
        this.templateRepository = templateRepository;
        this.companyRepository = companyRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Optional<Company> defaultCompany = companyRepository.findById(Company.DEFAULT_ID);
        if (defaultCompany.isEmpty()) {
            log.warn("[DEV] Default company {} not found yet; skipping template seeding.",
                    Company.DEFAULT_ID);
            return;
        }
        Company company = defaultCompany.get();

        TenantContext.set(Company.DEFAULT_ID);
        Session session = entityManager.unwrap(Session.class);
        session.enableFilter(FILTER_NAME).setParameter(FILTER_PARAM, Company.DEFAULT_ID);
        try {
            for (DefaultTemplate def : DefaultTemplate.ALL) {
                seedIfMissing(company, def);
            }
        } finally {
            try {
                session.disableFilter(FILTER_NAME);
            } catch (Exception ignored) {
                // Session may already be closed — swallow so we don't mask the real exception.
            }
            TenantContext.clear();
        }
    }

    private void seedIfMissing(Company company, DefaultTemplate def) {
        if (templateRepository.findDefaultByType(def.type()).isPresent()) {
            return;
        }
        Template t = new Template();
        t.setCompany(company);
        t.setName(def.name());
        t.setType(def.type());
        t.setContent(def.content());
        t.setDefault(true);
        templateRepository.save(t);
        log.warn("[DEV] Seeded default template: {} ({})", def.name(), def.type());
    }

    /**
     * Static catalogue of Arabic seed templates. Kept inside the seeder so the
     * runtime types ({@code Template}, repos) stay free of seed concerns.
     */
    private record DefaultTemplate(String name, DomainEnums.TemplateType type, String content) {

        private static final List<DefaultTemplate> ALL = List.of(
                new DefaultTemplate("عرض سعر", DomainEnums.TemplateType.QUOTATION, """
                        {{company.name}}
                        عرض أسعار

                        التاريخ: {{today}}
                        السيد/ة: {{client.name}}

                        نشكركم على ثقتكم بنا، ويسرنا تقديم عرض الأسعار التالي للخدمات القانونية المطلوبة:

                        ١- استشارة قانونية أولية: ........................
                        ٢- إعداد المذكرات والمستندات: ....................
                        ٣- المتابعة والترافع أمام الجهات المختصة: ........

                        إجمالي العرض: ........................

                        ملاحظات:
                        - صلاحية العرض: ٣٠ يومًا من تاريخه.
                        - تُسدد الأتعاب وفقًا للجدول المتفق عليه بين الطرفين.

                        مع خالص التحية،
                        {{company.name}}
                        """),

                new DefaultTemplate("فاتورة", DomainEnums.TemplateType.INVOICE, """
                        {{company.name}}
                        فاتورة

                        رقم الفاتورة: ........................
                        التاريخ: {{today}}
                        المرجع: قضية رقم {{case.number}}

                        العميل: {{client.name}}

                        البيان | المبلغ
                        - الأتعاب القانونية: ........................
                        - المصروفات والرسوم: ........................

                        الإجمالي: ........................

                        طريقة السداد: تحويل بنكي / نقدًا.
                        رقم الحساب: ........................

                        شكرًا لتعاملكم مع {{company.name}}.
                        """),

                new DefaultTemplate("إيصال قبض", DomainEnums.TemplateType.RECEIPT, """
                        {{company.name}}
                        إيصال قبض

                        التاريخ: {{today}}

                        استلمنا من السيد/ة: {{client.name}}
                        مبلغًا وقدره: ........................
                        وذلك سدادًا عن: قضية رقم {{case.number}}

                        التوقيع: ________________________
                        {{company.name}}
                        """),

                new DefaultTemplate("عقد خدمات قانونية",
                        DomainEnums.TemplateType.LEGAL_SERVICE_CONTRACT, """
                        عقد تقديم خدمات قانونية

                        أُبرم هذا العقد في {{today}} بين كل من:

                        الطرف الأول: {{company.name}} (ويُشار إليه فيما بعد بـ "المكتب").
                        الطرف الثاني: {{client.name}} (ويُشار إليه فيما بعد بـ "الموكِّل").

                        تمهيد:
                        لما كان الموكِّل يرغب في الاستعانة بالمكتب لتقديم الخدمات القانونية المتعلقة
                        بالقضية رقم {{case.number}}، فقد اتفق الطرفان وهما بكامل الأهلية المعتبرة شرعًا
                        وقانونًا على ما يلي:

                        البند الأول — نطاق الخدمات:
                        يلتزم المكتب بتقديم الاستشارات القانونية، وإعداد المذكرات والمستندات،
                        والترافع أمام الجهات المختصة في القضية الموضحة أعلاه.

                        البند الثاني — الأتعاب:
                        يستحق المكتب أتعابًا قدرها (........................) تُسدد على دفعات يتفق
                        عليها الطرفان.

                        البند الثالث — مدة العقد:
                        يسري هذا العقد اعتبارًا من تاريخ توقيعه وحتى انتهاء أعمال القضية.

                        البند الرابع — السرية:
                        يلتزم الطرفان بالسرية التامة بشأن جميع المعلومات المتبادلة بينهما.

                        البند الخامس — أحكام عامة:
                        حُرر هذا العقد من نسختين بيد كل طرف نسخة للعمل بها عند اللزوم.

                        الطرف الأول: {{company.name}}              الطرف الثاني: {{client.name}}
                        التوقيع: ____________________               التوقيع: ____________________
                        """),

                new DefaultTemplate("اتفاقية أتعاب",
                        DomainEnums.TemplateType.RETAINER_AGREEMENT, """
                        اتفاقية أتعاب محاماة

                        تاريخ التحرير: {{today}}

                        بين كل من:
                        - مكتب: {{company.name}} (يُشار إليه بـ "المكتب").
                        - السيد/ة: {{client.name}} (يُشار إليه بـ "الموكِّل").

                        الموضوع:
                        تنظيم الأتعاب المستحقة للمكتب نظير أعماله القانونية في القضية
                        رقم {{case.number}}.

                        أولًا — قيمة الأتعاب:
                        يتفق الطرفان على أن تكون الأتعاب الإجمالية مبلغًا قدره
                        (........................) تُسدد على النحو الآتي:
                        - دفعة مقدمة عند توقيع الاتفاقية: ........................
                        - دفعة عند انتهاء مرحلة الاستئناف: ........................
                        - دفعة عند صدور الحكم النهائي: ........................

                        ثانيًا — المصروفات:
                        تُحمَّل الرسوم القضائية والنفقات الإدارية على الموكِّل بشكل منفصل
                        عن الأتعاب أعلاه.

                        ثالثًا — الإنهاء:
                        يحق لأي من الطرفين إنهاء العلاقة وفقًا للأعراف المهنية، مع تسوية
                        المستحقات حتى تاريخ الإنهاء.

                        تحريرًا في {{today}}.

                        المكتب: {{company.name}}                  الموكِّل: {{client.name}}
                        التوقيع: ____________________              التوقيع: ____________________
                        """),

                new DefaultTemplate("وكالة",
                        DomainEnums.TemplateType.POWER_OF_ATTORNEY, """
                        وكالة قانونية

                        التاريخ: {{today}}

                        أنا الموقِّع أدناه: {{client.name}}
                        أوكِّل بموجب هذا المحرر السادة في مكتب: {{company.name}}
                        وذلك للقيام نيابةً عني بكل ما يلي بشأن القضية رقم {{case.number}}:

                        - الحضور أمام جميع المحاكم والنيابات العامة بكل درجاتها وأنواعها.
                        - تقديم العرائض والطلبات والمذكرات وسماع الشهود.
                        - الإقرار والصلح والتنازل وقبض المبالغ وتسوية المستحقات.
                        - تمثيلي أمام كافة الجهات الحكومية والإدارية المختصة.
                        - توكيل الغير في كل أو بعض ما ذُكر.

                        تظل هذه الوكالة سارية حتى يتم إلغاؤها كتابيًا.

                        الموكِّل: {{client.name}}
                        التوقيع: ____________________
                        """)
        );
    }
}
