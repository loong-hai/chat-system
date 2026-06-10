package com.hailong.chatsystem.service.impl;

import com.hailong.chatsystem.model.entity.User;
import com.hailong.chatsystem.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        User user = userRepository.findByIdentifier(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + identifier));

        if (user.isFrozen()) {
            throw new UsernameNotFoundException("账号已被冻结");
        }

        if (user.isDeregistered()) {
            throw new UsernameNotFoundException("账号已注销");
        }

        // 检查用户是否启用 - 新增
        if (!user.isEnabled()) {
            throw new UsernameNotFoundException("账号已被禁用");
        }

        return user;
    }
}