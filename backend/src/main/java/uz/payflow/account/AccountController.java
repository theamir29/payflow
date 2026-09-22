package uz.payflow.account;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uz.payflow.config.CurrentUserId;

@Tag(name = "Accounts", description = "Open accounts, read balances, look up a recipient")
@RestController
@RequestMapping("/api/accounts")
class AccountController {

    private final AccountService accountService;

    AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    List<AccountResponse> list(@CurrentUserId Long userId) {
        return accountService.list(userId);
    }

    @GetMapping("/{id}")
    AccountResponse get(@CurrentUserId Long userId, @PathVariable Long id) {
        return accountService.get(userId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AccountResponse open(@CurrentUserId Long userId, @Valid @RequestBody OpenAccountRequest request) {
        return accountService.open(userId, request);
    }

    @GetMapping("/lookup")
    RecipientResponse lookup(@RequestParam @Pattern(regexp = "\\d{20}", message = "Номер счёта: 20 цифр") String number) {
        return accountService.lookup(number);
    }
}
