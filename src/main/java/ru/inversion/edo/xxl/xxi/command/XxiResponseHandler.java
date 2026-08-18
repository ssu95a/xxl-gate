package ru.inversion.edo.xxl.xxi.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.inversion.edo.xxl.xxi.protocol.XXLRequest;
import ru.inversion.edo.xxl.xxi.protocol.XXLResponse;
import ru.inversion.edo.xxl.xxi.repo.RspRepository;

@Component
@RequiredArgsConstructor
public class XxiResponseHandler
{
   private final RspRepository repository;

   public XXLResponse send(XXLRequest request)
   {
      // следующий этап
   }
}