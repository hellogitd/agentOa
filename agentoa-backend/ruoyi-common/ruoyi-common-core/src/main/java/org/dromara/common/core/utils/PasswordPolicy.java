package org.dromara.common.core.utils;

import org.dromara.common.core.exception.ServiceException;
import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    private PasswordPolicy() { }
    public static void validate(String password) {
        if(password == null || password.length()<12 || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new ServiceException("密码至少 12 个字符，最多 72 个 UTF-8 字节");
    }
}
