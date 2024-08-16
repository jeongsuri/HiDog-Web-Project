package org.hidog.board.services;

import org.hidog.board.entities.Board;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

@SpringBootTest
public class BoardConfigInfoServiceTest {

    @Autowired
    private  BoardConfigInfoService infoService;

    @Test
    public void boardConfigTest(){
        Optional<Board> board = infoService.get("freetalk");
        System.out.println(board);
    }
}
