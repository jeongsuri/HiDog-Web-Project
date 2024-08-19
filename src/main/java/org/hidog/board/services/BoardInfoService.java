package org.hidog.board.services;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.hidog.board.controllers.BoardDataSearch;
import org.hidog.board.controllers.RequestBoard;
import org.hidog.board.entities.Board;
import org.hidog.board.entities.BoardData;
import org.hidog.board.entities.QBoardData;
import org.hidog.board.exceptions.BoardDataNotFoundException;
import org.hidog.board.exceptions.BoardNotFoundException;
import org.hidog.board.repositories.BoardDataRepository;
import org.hidog.board.repositories.BoardRepository;
import org.hidog.global.ListData;
import org.hidog.global.Pagination;
import org.hidog.global.Utils;
import org.hidog.global.constants.DeleteStatus;
import org.hidog.member.MemberUtil;
import org.hidog.member.entities.Member;
import org.modelmapper.ModelMapper;
import org.springframework.data.web.OffsetScrollPositionHandlerMethodArgumentResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
@RequiredArgsConstructor
public class BoardInfoService {
    private final EntityManager em;
    private final BoardDataRepository boardDataRepository;

    private final HttpServletRequest request;

    private final MemberUtil memberUtil;
    private final JPAQueryFactory jpaQueryFactory;
    private final OffsetScrollPositionHandlerMethodArgumentResolver offsetResolver;
    private final BoardRepository boardRepository;
    private final BoardConfigInfoService configInfoService;
    private final Utils utils;

    /**
     * 게시글 목록 조회
     *
     */
    public ListData<BoardData> getList(BoardDataSearch search, DeleteStatus status) {

        String bid = search.getBid();
        List<String> bids = search.getBids(); // 게시판 여러개 조회

        // 게시판 설정 조회
        Board board = bid != null && StringUtils.hasText(bid.trim()) ? configInfoService.get(bid.trim()).orElseThrow(BoardNotFoundException::new) : new Board();

        int page =  Math.max(search.getPage(), 1);
        int limit = search.getLimit();
        limit = limit > 0 ? limit : board.getRowsPerPage();

        int offset = (page - 1) * limit;

        // 삭제가 되지 않은 게시글 목록이 기본 값
        status = Objects.requireNonNullElse(status, DeleteStatus.UNDELETED);

        String sopt = search.getSort(); // 검색 옵션
        String skey = search.getSkey(); // 검색 키워드


        /* 검색 처리 S*/
        QBoardData boardData = QBoardData.boardData;
        BooleanBuilder andBuilder = new BooleanBuilder();

        // 삭제, 미삭제 게시글 조회 처리
        if (status != DeleteStatus.ALL) {
            if (status == DeleteStatus.UNDELETED) {
                andBuilder.and(boardData.deletedAt.isNull()); // 미삭제 게시글
            } else {
                andBuilder.and(boardData.deletedAt.isNotNull()); // 삭제된 게시글
            }
        }


        if (bid != null && StringUtils.hasText(bid.trim())) { // 게시판별 조회
            bids = List.of(bid);
        }

        if (bids != null && !bids.isEmpty()){ // 게시판 여러개 조회
            andBuilder.and(boardData.board.bid.in(bids));
        }

        /**
         * 조건 검색 처리
         *
         * sopt - ALL : 통합검색(제목 + 내용 + 글작성자(작성자, 회원명))
         *       SUBJECT : 제목검색
         *       CONTENT : 내용검색
         *       SUBJECT_CONTENT: 제목 + 내용 검색
         *       NAME : 이름(작성자, 회원명)
         */
        sopt = sopt != null && StringUtils.hasText(sopt.trim()) ? sopt.trim() : "ALL";
        if (skey != null && StringUtils.hasText(skey.trim())) {
            skey = skey.trim();
            BooleanExpression condition = null;

            BooleanBuilder orBuilder = new BooleanBuilder();

            /* 이름 검색 S */
            BooleanBuilder nameCondition = new BooleanBuilder();
            nameCondition.or(boardData.poster.contains(skey));
            if (boardData.member != null) {
                nameCondition.or(boardData.member.userName.contains(skey));
            }
            /* 이름 검색 E */

            if (sopt.equals("ALL")) { // 통합 검색
                orBuilder.or(boardData.subject.concat(boardData.content)
                                .contains(skey))
                        .or(nameCondition);



            } else if (sopt.equals("SUBJECT")) { // 제목 검색
                condition = boardData.subject.contains(skey);
            } else if (sopt.equals("CONTENT")) { // 내용 검색
                condition = boardData.content.contains(skey);
            } else if (sopt.contains("SUBJECT_CONTENT")) { // 제목 + 내용 검색
                condition = boardData.subject.concat(boardData.content)
                        .contains(skey);
            } else if (sopt.equals("NAME")) {
                andBuilder.and(nameCondition);
            }

            if (condition != null) andBuilder.and(condition);
            andBuilder.and(orBuilder);
        }

        /* 검색 처리 E */

        /* 정렬 처리 S */
        String sort = search.getSort();

        PathBuilder<BoardData> pathBuilder = new PathBuilder<>(BoardData.class, "boardData");
        OrderSpecifier orderSpecifier = null;
        Order order = Order.DESC;
        if (sort != null && StringUtils.hasText(sort.trim())) {
            // 정렬항목_방향   예) viewCount_DESC -> 조회수가 많은 순으로 정렬
            String[] _sort = sort.split("_");
            if (_sort[1].toUpperCase().equals("ASC")) {
                order = Order.ASC;
            }

            orderSpecifier = new OrderSpecifier(order, pathBuilder.get(_sort[0]));
        }

        List<OrderSpecifier> orderSpecifiers = new ArrayList<>();
        orderSpecifiers.add(boardData.notice.desc()); // 공지사항이 가장 먼저 나오기
        if (orderSpecifier != null) {
            orderSpecifiers.add(orderSpecifier);
        }
        orderSpecifiers.add(boardData.createdAt.desc());
        /* 정렬 처리 E */

        /* 목록 조회 처리 S */
        List<BoardData> items = jpaQueryFactory
                .selectFrom(boardData)
                .leftJoin(boardData.board)
                .fetchJoin()
                .leftJoin(boardData.member)
                .fetchJoin()
                .where(andBuilder)
                .orderBy(orderSpecifiers.toArray(OrderSpecifier[]::new))
                .offset(offset)
                .limit(limit)
                .fetch();

        // 추가 정보 처리
        items.forEach(this::addInfo);

        /* 목록 조회 처리 E */

        // 전체 게시글 갯수
        long total = boardRepository.count(andBuilder);

        // 페이징 처리
        int ranges = utils.isMobile() ? board.getPageCountMobile() : board.getPageCountPc();
        Pagination pagination = new Pagination(page, (int)total, ranges, limit, request);

        return new ListData<>(items, pagination);
    }

    /**
     * 게시판 별 목록
     * 특정 게시판 목록을 조회
     *
     * @param bid : 게시판 ID
     * @param search
     * @return
     */
    public ListData<BoardData> getList(String bid, BoardDataSearch search, DeleteStatus status) {
        search.setBid(bid);

        return getList(search, status);
    }

    public ListData<BoardData> getList(String bid, BoardDataSearch search) {
        return getList(bid, search, DeleteStatus.UNDELETED);
    }

    /**
     * 게시글 1개 조회(엔티티)
     *
     * @param seq : 게시글 번호
     * @return
     */
    public BoardData get(Long seq, DeleteStatus status) {

        BooleanBuilder andBuilder = new BooleanBuilder();
        QBoardData boardData = QBoardData.boardData;
        andBuilder.and(boardData.seq.eq(seq));

        // 삭제, 미삭제 게시글 조회 처리
        if (status != DeleteStatus.ALL) {
            if (status == DeleteStatus.UNDELETED) {
                andBuilder.and(boardData.deletedAt.isNull()); // 미삭된 게시글
            } else {
                andBuilder.and(boardData.deletedAt.isNotNull()); // 삭제된 게시글
            }
        }

        BoardData item = jpaQueryFactory.selectFrom(boardData)
                .leftJoin(boardData.board)
                .fetchJoin()
                .leftJoin(boardData.member)
                .fetchJoin()
                .where(andBuilder)
                .fetchFirst();

        if (item == null) {
            throw new BoardDataNotFoundException();
        }

        // 추가 데이터 처리
        addInfo(item);

        return item;
    }

    public BoardData get(Long seq) {
        return get(seq, DeleteStatus.UNDELETED);
    }

    /**
     * BoardData(엔티티) -> RequestBoard(커맨드객체)
     * 게시글 데이터(BoardData), 게시글 번호(Long)
     * @return
     */
    public RequestBoard getForm(Long seq, DeleteStatus status) {
        BoardData item = get(seq, status);

        return getForm(item, status);
    }

    public RequestBoard getForm(BoardData item, DeleteStatus status) {
        RequestBoard form = new ModelMapper().map(item, RequestBoard.class);
        form.setGuest(item.getMember() == null);

        return form;
    }

    public BoardData getForm(Long seq) {
        return get(seq, DeleteStatus.UNDELETED);
    }

    public RequestBoard getForm(BoardData item) {
        return getForm(item, DeleteStatus.UNDELETED);
    }


    /**
     * 게시글 추가 정보 처리, 추가 데이터 처리
     *          - 업로드한 파일 목록
     *          에디터 이미지 목록, 첨부 파일 이미지 목록
     *          - 권한 : 글쓰기, 글수정, 글 삭제, 글 조회 가능 여부
     *          - 댓글
     * @param item
     */
    public void addInfo(BoardData item) {
        /* 수정, 삭제 권한 정보 처리 S */
        boolean editable = false, deletable = false, mine = false;
        Member _member = item.getMember(); // null - 비회원, X null -> 회원

        // 관리자 -> 삭제, 수정 모두 가능
        if (memberUtil.isAdmin()) {
            editable = true;
            deletable = true;
        }

        // 회원 -> 직접 작성한 게시글만 삭제, 수정 가능
        Member member = memberUtil.getMember();
        if (_member != null && memberUtil.isLogin() && _member.getEmail().equals(member.getEmail())) {
            editable = true;
            deletable = true;
            mine = true;
        }

        // 비회원 -> 비회원 비밀번호가 확인 된 경우 삭제, 수정 가능
        // 비회원 비밀번호 인증 여부 세션에 있는 guest_confirmed_게시글번호 true -> 인증
        HttpSession session = request.getSession();
        String key = "guest_confirmed_" + item.getSeq();
        Boolean guestConfirmed = (Boolean)session.getAttribute(key);
        if (_member == null && guestConfirmed != null && guestConfirmed) {
            editable = true;
            deletable = true;
            mine = true;
        }

        item.setEditable(editable);
        item.setDeletable(deletable);
        item.setMine(mine);

        // 수정 버튼 노출 여부
        // 관리자 - 노출, 회원 게시글 - 직접 작성한 게시글, 비회원
        boolean showEditButton = memberUtil.isAdmin() || mine || _member == null;
        boolean showDeleteButton = showEditButton;

        item.setShowEditButton(showEditButton);
        item.setShowDeleteButton(showDeleteButton);

        /* 수정, 삭제 권한 정보 처리 E */
    }
}
