package uz.payflow.stats;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uz.payflow.account.Currency;
import uz.payflow.config.CurrentUserId;

@Tag(name = "Stats", description = "Income and expense by month")
@RestController
@RequestMapping("/api/stats")
class StatsController {

    private final StatsService statsService;

    StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/monthly")
    List<MonthlyStat> monthly(@CurrentUserId Long userId,
                              @RequestParam(defaultValue = "UZS") Currency currency,
                              @RequestParam(defaultValue = "6")
                              @Min(value = 1, message = "От 1 до 12 месяцев")
                              @Max(value = 12, message = "От 1 до 12 месяцев") int months) {
        return statsService.monthly(userId, currency, months);
    }
}
