package com.yumyumcoach.domain.ai.chat.mapper;

import com.yumyumcoach.domain.ai.chat.entity.AiChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AiChatMessageMapper {
    int insertMessages(@Param("messages") List<AiChatMessage> messages);

    List<AiChatMessage> findByEmailAndDate(@Param("email") String email,
                                           @Param("messageDate") LocalDate messageDate);
}
