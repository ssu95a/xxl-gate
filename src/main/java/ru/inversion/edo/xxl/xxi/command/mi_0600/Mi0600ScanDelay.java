package ru.inversion.edo.xxl.xxi.command.mi_0600;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.NamedNativeQueries;
import javax.persistence.NamedNativeQuery;

/**
 * Read-only WSP=600 scan configuration.
 * SQL dialect selection is handled by SQLDataSet, not the repository.
 */
@Entity(name = "Mi0600ScanDelay")
@NamedNativeQueries({
      @NamedNativeQuery(
            name = "Mi0600ScanDelay.pg",
            query = """
                  SELECT 600 AS wsp_id,
                         MI_prp.get_Wsp_Property(600, 'SCAN_DELAY')::numeric AS scan_delay
                  """
      ),
      @NamedNativeQuery(
            name = "Mi0600ScanDelay",
            query = """
                  SELECT 600 AS wsp_id,
                         TO_NUMBER(MI_Prp.get_Wsp_Property(600, 'SCAN_DELAY')) AS scan_delay
                    FROM DUAL
                  """
      )
})
public class Mi0600ScanDelay
{
   private Integer wspId;
   private Long scanDelay;

   @Id
   @Column(name = "wsp_id")
   public Integer getWspId() { return wspId; }
   public void setWspId(Integer value) { wspId = value; }

   @Column(name = "scan_delay")
   public Long getScanDelay() { return scanDelay; }
   public void setScanDelay(Long value) { scanDelay = value; }
}
