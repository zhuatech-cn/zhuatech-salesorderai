/* Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ */
package cn.zhuatech.salesorderai.practical;

import static cn.zhuatech.salesorderai.practical.PracticalInput.*;

import java.math.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
@Service
public class PracticalService {
  /** 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ 商业授权或定制开发请微信添加微信号zhuatech或zhuatech2进行咨询。 */
  public Map<String, Object> execute(Map<String, Object> r) {
    validate(r);
    keys(r, "creditLimit", "outstanding", "requestedDate", "minimumMarginPercent", "lines");
    var limit = money(r, "creditLimit");
    var outstanding = money(r, "outstanding");
    var requested = date(text(r, "requestedDate", 10));
    var minimum = decimal(r, "minimumMarginPercent", 0, 100);
    Set<String> ids = new HashSet<>();
    BigDecimal net = BigDecimal.ZERO, gross = BigDecimal.ZERO, cost = BigDecimal.ZERO;
    List<Map<String, Object>> out = new ArrayList<>();
    boolean ready = true;
    for (var line : rows(r, "lines", 1, 300)) {
      keys(
          line,
          "id",
          "quantity",
          "stock",
          "unitPrice",
          "unitCost",
          "discountPercent",
          "taxPercent",
          "readyDate");
      String id = id(line, "id");
      unique(ids, id);
      int quantity = integer(line, "quantity", 1, 1000000),
          stock = integer(line, "stock", 0, 1000000000);
      var price = money(line, "unitPrice");
      var unitCost = money(line, "unitCost");
      var discount = decimal(line, "discountPercent", 0, 100).movePointLeft(2);
      var tax = decimal(line, "taxPercent", 0, 50).movePointLeft(2);
      var exactNet =
          price.multiply(BigDecimal.ONE.subtract(discount)).multiply(BigDecimal.valueOf(quantity));
      var lineNet = exactNet.setScale(2, RoundingMode.HALF_UP);
      var lineTax = exactNet.multiply(tax).setScale(2, RoundingMode.HALF_UP);
      var lineCost = unitCost.multiply(BigDecimal.valueOf(quantity));
      List<String> issues = new ArrayList<>();
      if (stock < quantity) issues.add("STOCK_SHORTFALL");
      if (date(text(line, "readyDate", 10)).isAfter(requested)) issues.add("DELIVERY_LATE");
      if (exactNet.signum() == 0
          || exactNet
                  .subtract(lineCost)
                  .multiply(new BigDecimal("100"))
                  .compareTo(minimum.multiply(exactNet))
              < 0) issues.add("MARGIN_REVIEW");
      if (!issues.isEmpty()) ready = false;
      net = net.add(lineNet);
      gross = gross.add(lineNet).add(lineTax);
      cost = cost.add(lineCost);
      out.add(
          Map.of(
              "id",
              id,
              "net",
              lineNet,
              "tax",
              lineTax,
              "cost",
              lineCost,
              "stockShortfall",
              Math.max(0, quantity - stock),
              "issues",
              issues));
    }
    var exposure = outstanding.add(gross);
    boolean credit = exposure.compareTo(limit) <= 0;
    return Map.of(
        "lines",
        out,
        "netAmount",
        net,
        "grossAmount",
        gross,
        "totalCost",
        cost,
        "creditExposure",
        exposure,
        "creditWithinLimit",
        credit,
        "readyForReview",
        ready && credit,
        "automaticRelease",
        false);
  }
}
