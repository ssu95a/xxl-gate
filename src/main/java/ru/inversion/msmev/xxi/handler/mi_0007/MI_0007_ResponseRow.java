package ru.inversion.msmev.xxi.handler.mi_0007;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MI_0007_ResponseRow {

   private String docStatus;

   private String invalidityReason;
   private String invaliditySince;

   @JsonFormat(pattern = "yyyy-MM-dd")
   private LocalDate issueDate;

   private String issuerCode;
   private String comment;

   @JsonIgnore
   private UUID requestUuid;
   @JsonIgnore
   private UUID itemUuid;
   @JsonIgnore
   private UUID messageUuid;
   @JsonIgnore
   private int itemIndex;
}
