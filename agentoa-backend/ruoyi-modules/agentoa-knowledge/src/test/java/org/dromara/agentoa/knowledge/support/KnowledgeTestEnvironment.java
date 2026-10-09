package org.dromara.agentoa.knowledge.support;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import org.dromara.agentoa.knowledge.domain.OaDocument;
import org.dromara.agentoa.knowledge.domain.OaDocumentAcl;
import org.dromara.agentoa.knowledge.domain.OaDocumentFile;
import org.dromara.agentoa.knowledge.domain.OaDocumentRevision;
import org.dromara.agentoa.knowledge.domain.OaDocumentVersion;
import org.dromara.agentoa.knowledge.domain.OaKnowledgeMember;
import org.dromara.agentoa.knowledge.domain.OaKnowledgeSpace;
import org.dromara.agentoa.knowledge.domain.enums.SpaceRole;
import org.dromara.agentoa.knowledge.mapper.KnowledgeIdentityMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentAclMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentFileMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentCommentMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentLikeMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentFavoriteMapper;
import org.dromara.agentoa.knowledge.service.impl.DocumentSocialServiceImpl;
import org.dromara.agentoa.knowledge.mapper.OaDocumentMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentRevisionMapper;
import org.dromara.agentoa.knowledge.mapper.OaDocumentVersionMapper;
import org.dromara.agentoa.knowledge.mapper.OaKnowledgeMemberMapper;
import org.dromara.agentoa.knowledge.mapper.OaKnowledgeSpaceMapper;
import org.dromara.agentoa.knowledge.service.impl.KnowledgeDocumentServiceImpl;
import org.dromara.agentoa.knowledge.service.impl.KnowledgeFileServiceImpl;
import org.dromara.agentoa.knowledge.service.impl.KnowledgeSpaceServiceImpl;
import org.dromara.agentoa.knowledge.service.support.KnowledgeAuthorization;
import org.dromara.agentoa.knowledge.service.support.KnowledgeFileStorage;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.enums.UserType;
import org.dromara.common.satoken.utils.LoginHelper;
import org.h2.jdbcx.JdbcConnectionPool;
import org.mockito.Mockito;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.core.ResponseBytes;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * H2 + MyBatis-Plus 测试环境（真实 Mapper、真实服务实现、内存 S3 桩）。
 */
public final class KnowledgeTestEnvironment {

    public static final Long USER_OWNER = 100L;
    public static final Long USER_EDITOR = 200L;
    public static final Long USER_VIEWER = 300L;
    public static final Long USER_OUTSIDER = 400L;

    private static DataSource dataSource;
    private static TransactionTemplate txTemplate;

    /** 内存对象存储桩：bucket/key -> bytes */
    public static final Map<String, byte[]> BLOBS = new ConcurrentHashMap<>();

    public static OaKnowledgeSpaceMapper spaces;
    public static OaKnowledgeMemberMapper members;
    public static OaDocumentMapper documents;
    public static OaDocumentVersionMapper versions;
    public static OaDocumentRevisionMapper revisions;
    public static OaDocumentAclMapper acls;
    public static OaDocumentFileMapper files;
    public static OaDocumentCommentMapper comments;
    public static OaDocumentLikeMapper likes;
    public static OaDocumentFavoriteMapper favorites;
    public static KnowledgeIdentityMapper identity;

    public static KnowledgeAuthorization authorization;
    public static KnowledgeFileStorage storage;
    public static KnowledgeSpaceServiceImpl spaceService;
    public static KnowledgeDocumentServiceImpl documentService;
    public static KnowledgeFileServiceImpl fileService;
    public static DocumentSocialServiceImpl socialService;

    private KnowledgeTestEnvironment() {
    }

    public static synchronized void bootstrap() {
        if (dataSource != null) {
            return;
        }
        dataSource = JdbcConnectionPool.create(
            "jdbc:h2:mem:kntest-" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR", "sa", "");
        runSchema();

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        GlobalConfig globalConfig = GlobalConfigUtils.defaults();
        globalConfig.setBanner(false);
        globalConfig.getDbConfig().setIdType(IdType.ASSIGN_ID);
        factoryBean.setGlobalConfig(globalConfig);
        MybatisConfiguration configuration = new MybatisConfiguration();
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(com.baomidou.mybatisplus.annotation.DbType.H2));
        configuration.addInterceptor(interceptor);
        factoryBean.setConfiguration(configuration);
        SqlSessionTemplate sqlSession;
        try {
            var factory = factoryBean.getObject();
            var mapperConfiguration = factory.getConfiguration();
            for (Class<?> mapper : List.of(OaKnowledgeSpaceMapper.class, OaKnowledgeMemberMapper.class,
                OaDocumentMapper.class, OaDocumentVersionMapper.class, OaDocumentRevisionMapper.class,
                OaDocumentAclMapper.class, OaDocumentFileMapper.class,
                OaDocumentCommentMapper.class, OaDocumentLikeMapper.class, OaDocumentFavoriteMapper.class)) {
                mapperConfiguration.addMapper(mapper);
            }
            mapperConfiguration.addMapper(KnowledgeIdentityMapper.class);
            sqlSession = new SqlSessionTemplate(factory);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build test SqlSession", e);
        }

        spaces = sqlSession.getMapper(OaKnowledgeSpaceMapper.class);
        members = sqlSession.getMapper(OaKnowledgeMemberMapper.class);
        documents = sqlSession.getMapper(OaDocumentMapper.class);
        versions = sqlSession.getMapper(OaDocumentVersionMapper.class);
        revisions = sqlSession.getMapper(OaDocumentRevisionMapper.class);
        acls = sqlSession.getMapper(OaDocumentAclMapper.class);
        files = sqlSession.getMapper(OaDocumentFileMapper.class);
        identity = sqlSession.getMapper(KnowledgeIdentityMapper.class);

        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        txTemplate = new TransactionTemplate(txManager);

        authorization = new KnowledgeAuthorization(spaces, members, acls, documents, identity);
        storage = new KnowledgeFileStorage(mockS3(), new JdbcTemplate(dataSource));
        ReflectionTestUtils.setField(storage, "bucket", "test-bucket");
        spaceService = new KnowledgeSpaceServiceImpl(spaces, members, documents, files, identity, authorization);
        documentService = new KnowledgeDocumentServiceImpl(documents, versions, revisions, spaces, identity, authorization);
        fileService = new KnowledgeFileServiceImpl(files, documents, spaces, identity, authorization, storage);
        comments = sqlSession.getMapper(OaDocumentCommentMapper.class);
        likes = sqlSession.getMapper(OaDocumentLikeMapper.class);
        favorites = sqlSession.getMapper(OaDocumentFavoriteMapper.class);
        socialService = new DocumentSocialServiceImpl(comments, likes, favorites, documents, authorization, identity);
    }

    /** 内存 S3 桩：putObject/getObjectAsBytes 走 {@link #BLOBS} */
    private static S3Client mockS3() {
        S3Client s3 = Mockito.mock(S3Client.class);
        Mockito.when(s3.putObject(Mockito.any(PutObjectRequest.class), Mockito.any(RequestBody.class)))
            .thenAnswer(invocation -> {
                PutObjectRequest request = invocation.getArgument(0);
                RequestBody body = invocation.getArgument(1);
                try (InputStream in = body.contentStreamProvider().newStream()) {
                    BLOBS.put(request.key(), in.readAllBytes());
                }
                return PutObjectResponse.builder().build();
            });
        Mockito.when(s3.getObjectAsBytes(Mockito.any(GetObjectRequest.class)))
            .thenAnswer(invocation -> {
                GetObjectRequest request = invocation.getArgument(0);
                byte[] bytes = BLOBS.get(request.key());
                if (bytes == null) {
                    throw new IllegalStateException("no blob for " + request.key());
                }
                return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), bytes);
            });
        Mockito.when(s3.deleteObject(Mockito.any(software.amazon.awssdk.services.s3.model.DeleteObjectRequest.class)))
            .thenAnswer(invocation -> {
                software.amazon.awssdk.services.s3.model.DeleteObjectRequest request = invocation.getArgument(0);
                BLOBS.remove(request.key());
                return software.amazon.awssdk.services.s3.model.DeleteObjectResponse.builder().build();
            });
        return s3;
    }

    public static <T> T inTransaction(Supplier<T> action) {
        return txTemplate.execute(status -> action.get());
    }

    public static void clearData() {
        for (String table : List.of("oa_knowledge_space", "oa_knowledge_member", "oa_document",
            "oa_document_version", "oa_document_revision", "oa_document_acl", "oa_document_file",
            "oa_document_comment", "oa_document_like", "oa_document_favorite",
            "sys_file", "sys_user", "sys_role", "sys_user_role")) {
            jdbcTemplate().execute("DELETE FROM " + table);
        }
        BLOBS.clear();
    }

    public static void loginAs(Long userId, Set<String> menuPermission) {
        cn.dev33.satoken.context.mock.SaTokenContextMockUtil.setMockContext();
        LoginUser user = new LoginUser();
        user.setUserId(userId);
        user.setUsername("u" + userId);
        user.setNickname("用户" + userId);
        user.setUserType(UserType.SYS_USER.getUserType());
        user.setTenantId("000000");
        user.setDeptId(10L);
        user.setDeptName("测试部门");
        user.setMenuPermission(menuPermission == null ? Set.of() : menuPermission);
        user.setRolePermission(Set.of());
        var param = new cn.dev33.satoken.stp.parameter.SaLoginParameter();
        param.setExtra(LoginHelper.CLIENT_KEY, "test-client");
        LoginHelper.login(user, param);
    }

    public static void logout() {
        try {
            cn.dev33.satoken.stp.StpUtil.logout();
        } catch (Exception ignored) {
            // 未登录时忽略
        }
    }

    public static void seedUser(Long userId, String nickname) {
        jdbcTemplate().update("INSERT INTO sys_user(user_id, dept_id, user_name, nick_name) VALUES(?,?,?,?)",
            userId, 10L, "u" + userId, nickname);
    }

    public static void seedRole(long roleId, String roleKey, Long userId) {
        jdbcTemplate().update("INSERT INTO sys_role(role_id, role_name, role_key) VALUES(?,?,?)",
            roleId, roleKey, roleKey);
        if (userId != null) {
            jdbcTemplate().update("INSERT INTO sys_user_role(user_id, role_id) VALUES(?,?)", userId, roleId);
        }
    }

    /** 直接落库一个空间（含可选成员），绕过服务便于构造越权场景 */
    public static OaKnowledgeSpace seedSpace(long id, String name, int spaceType, Long creatorId) {
        OaKnowledgeSpace space = new OaKnowledgeSpace();
        space.setId(id);
        space.setName(name);
        space.setSpaceType(spaceType);
        space.setCreateBy(creatorId);
        space.setCreateTime(new Date());
        spaces.insert(space);
        return space;
    }

    public static OaKnowledgeMember seedMember(long id, long spaceId, Long userId, SpaceRole role) {
        OaKnowledgeMember member = new OaKnowledgeMember();
        member.setId(id);
        member.setSpaceId(spaceId);
        member.setUserId(userId);
        member.setRole(role.code());
        member.setGrantedBy(userId);
        member.setGrantedTime(new Date());
        member.setCreateTime(new Date());
        members.insert(member);
        return member;
    }

    public static OaDocument seedDocument(long id, long spaceId, String title, String content) {
        OaDocument document = new OaDocument();
        document.setId(id);
        document.setSpaceId(spaceId);
        document.setParentId(0L);
        document.setTitle(title);
        document.setContent(content);
        document.setContentText(content);
        document.setDocType("markdown");
        document.setVersion(1);
        document.setStatus(1);
        document.setIsTop(0);
        document.setViewCount(0);
        document.setCreateBy(USER_OWNER);
        document.setCreateTime(new Date());
        document.setUpdateTime(new Date());
        documents.insert(document);
        return document;
    }

    public static JdbcTemplate jdbcTemplate() {
        return new JdbcTemplate(dataSource);
    }

    private static void runSchema() {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            StringBuilder sql = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("kn-test-schema.sql").getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sql.append(line).append('\n');
                }
            }
            for (String part : sql.toString().split(";")) {
                if (!part.isBlank()) {
                    statement.execute(part);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create test schema", e);
        }
    }
}
