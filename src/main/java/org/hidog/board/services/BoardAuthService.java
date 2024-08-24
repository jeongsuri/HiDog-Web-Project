package org.hidog.board.services;

import lombok.RequiredArgsConstructor;
import org.hidog.board.entities.Board;
import org.hidog.board.entities.BoardData;
import org.hidog.board.exceptions.BoardDataNotFoundException;
import org.hidog.board.exceptions.BoardNotFoundException;
import org.hidog.board.exceptions.GuestPasswordCheckException;
import org.hidog.global.exceptions.UnAuthorizedException;
import org.hidog.member.MemberUtil;
import org.hidog.member.constants.Authority;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BoardAuthService {
    private final MemberUtil memberUtil;
    private final BoardConfigInfoService configInfoService;
    private final BoardInfoService infoService;

    private Board board;
    private BoardData boardData;

    /**
     *  권한 체크
     * @param mode
     *          - list, write, update, view ..
     * @param seq : 게시글 번호
     */
    public void check(String mode, Long seq) {

        // 관리자는 권한 체크 X
        if (memberUtil.isAdmin()) {
            return;
        }

        if (seq != null && seq.longValue() != 0L) {
            boardData = infoService.get(seq);
        }

        if (board == null) {
            board = boardData.getBoard();
        }

        //게시글 사용 여부 체크
        if(!board.isActive()){
            throw new BoardDataNotFoundException();
        }

        // 게시글 목록 접근 권한 체크
        Authority authority = board.getListAccessType();
        if (mode.equals("list") && ((authority == Authority.USER && !memberUtil.isLogin()) || (authority == Authority.ADMIN && !memberUtil.isAdmin()))) {
            throw new UnAuthorizedException();
        }

        // 게시글 보기 접근 권한 체크
        Authority viewAuthority = board.getViewAccessType();
        if (mode.equals("view") && ((viewAuthority == Authority.USER && !memberUtil.isLogin()) || (viewAuthority == Authority.ADMIN && !memberUtil.isAdmin()))) {
            throw new UnAuthorizedException();
        }

        // 글쓰기 접근 권한 체크
        Authority writeAuthority = board.getWriteAccessType();
        if (mode.equals("write") && ((writeAuthority == Authority.USER && !memberUtil.isLogin()) || (writeAuthority == Authority.ADMIN && !memberUtil.isAdmin()))) {
            throw new UnAuthorizedException();
        }

        /**
         * 글 수정, 삭제 - 작성자만 수정 가능
         *      - 회원 게시글은 로그인한 사용자와 일치여부
         *      - 비회원 게시글은 인증 여부 체크 -> 인증 X -> 비밀번호 확인 페이지로 이동 검증
         *      - 검증 완료된 경우, 문제 X
         */
        if (List.of("update", "delete").contains(mode) && !boardData.isEditable()) {
            if (boardData.getMember() == null) {
                // 비회원 게시글 - 비밀번호 검증 필요
                throw new GuestPasswordCheckException();
            }

            throw new UnAuthorizedException();
        }
    }

    /**
     *
     * @param bid - 게시판 ID
     * @param mode - write, list
     */
    public void check(String mode, String bid) {
        board = configInfoService.get(bid).orElseThrow(BoardNotFoundException::new);

        check(mode, 0L);
    }
}