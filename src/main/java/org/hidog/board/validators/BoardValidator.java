package org.hidog.board.validators;

import lombok.RequiredArgsConstructor;
import org.hidog.board.controllers.RequestBoard;
import org.hidog.member.MemberUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

@Component
@RequiredArgsConstructor
public class BoardValidator implements Validator {

    private final MemberUtil memberUtil;

    @Override
    public boolean supports(Class<?> clazz) {
        return clazz.isAssignableFrom(RequestBoard.class);
    }

    @Override
    public void validate(Object target, Errors errors) {
        RequestBoard form = (RequestBoard) target;


        // 비회원 비밀번호 유효성 검사(비로그인 일 시 게시글 수정, 삭제할 때 비밀번호 확인하기, 로그인 시에는 비밀번호 확인x)
        if (!memberUtil.isLogin()) {
            String guestPw = form.getGuestPw();
            if(!StringUtils.hasText(guestPw)) {
                errors.rejectValue("guestPw", "NotBlank");
            } else {
                /**
                 * 비밀번호 복잡성
                 * 
                 */
            }
        }
    }
}