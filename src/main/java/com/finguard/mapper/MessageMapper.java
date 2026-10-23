package com.finguard.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.finguard.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}
