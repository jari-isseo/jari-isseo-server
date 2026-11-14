package com.jariisseo.auth;

import com.jariisseo.auth.dto.LoginRequest;
import com.jariisseo.auth.dto.LoginResponse;
import com.jariisseo.auth.dto.SignupRequest;
import com.jariisseo.auth.dto.SignupResponse;
import com.jariisseo.global.error.ApiException;
import com.jariisseo.global.error.ErrorCode;
import com.jariisseo.store.PublicIdGenerator;
import com.jariisseo.store.Store;
import com.jariisseo.store.StoreRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final StoreRepository storeRepository;
    private final PublicIdGenerator publicIdGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthService(StoreRepository storeRepository, PublicIdGenerator publicIdGenerator,
                       PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
        this.storeRepository = storeRepository;
        this.publicIdGenerator = publicIdGenerator;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    // 가입 = 매장 생성 (매장당 계정 1개). 가입하자마자 로그인된 상태가 되도록 토큰도 함께 준다
    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (storeRepository.existsByLoginId(request.loginId())) {
            throw new ApiException(ErrorCode.LOGIN_ID_TAKEN);
        }

        Store store = new Store(
                publicIdGenerator.generate(),
                request.storeName().strip(),
                request.loginId(),
                passwordEncoder.encode(request.password()));
        try {
            storeRepository.saveAndFlush(store);
        } catch (DataIntegrityViolationException e) {
            // 위에서 확인한 직후 같은 아이디로 동시에 가입한 경우 → DB UNIQUE 제약이 막아줌
            throw new ApiException(ErrorCode.LOGIN_ID_TAKEN);
        }

        return new SignupResponse(
                jwtTokenService.issue(store.getId()),
                new SignupResponse.StoreSummary(store.getPublicId(), store.getName()));
    }

    // 아이디가 없든 비밀번호가 틀리든 같은 에러 → 어떤 아이디가 가입돼 있는지 알려주지 않기 위해
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        Store store = storeRepository.findByLoginId(request.loginId())
                .filter(s -> passwordEncoder.matches(request.password(), s.getPasswordHash()))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        return new LoginResponse(jwtTokenService.issue(store.getId()), jwtTokenService.expiresInSeconds());
    }
}
