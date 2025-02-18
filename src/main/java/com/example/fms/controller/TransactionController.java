package com.example.fms.controller;

import com.example.fms.dto.TransactionExpenseDTO;
import com.example.fms.dto.TransactionIncomeDTO;
import com.example.fms.dto.TransactionRemittanceDTO;
import com.example.fms.entity.ResponseMessage;
import com.example.fms.entity.Transaction;
import com.example.fms.repository.UserRepository;
import com.example.fms.service.TransactionService;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiParam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@CrossOrigin
@RestController
@RequestMapping("/transaction")
public class TransactionController {

    private final TransactionService transactionService;
    private final UserRepository userRepository;

    TransactionController(TransactionService transactionService, UserRepository userRepository) {
        this.transactionService = transactionService;
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transaction> getById(@PathVariable("id") Long id, Principal principal){
        String email = principal.getName();
        if (userRepository.findByEmail(email).getRole().getName().equals("ROLE_ADMIN"))
            return transactionService.getByIdForAdmin(id);
        else return transactionService.getByIdForUser(id, principal.getName());
    }

    @ApiImplicitParams({
            @ApiImplicitParam(name = "page", dataType = "int", paramType = "query",
                    value = "Results page you want to retrieve (0..N)"),
            @ApiImplicitParam(name = "size", dataType = "int", paramType = "query",
                    value = "Number of records per page."),
    })
    @GetMapping("/get")
    public Page<Transaction> getAllByParam(Pageable pageable,
                                           @RequestParam(value = "isDeleted", required = false, defaultValue = "false") boolean isDeleted,
                                           @RequestParam(required = false) String action,
                                           @RequestParam(required = false) Long fromAccountId,
                                           @RequestParam(required = false) Long categoryId,
                                           @RequestParam(required = false) Long toAccountId,
                                           @RequestParam(required = false) BigDecimal balanceLessThan,
                                           @RequestParam(required = false) BigDecimal balanceGreaterThan,
                                           @RequestParam(required = false) Long userId,
                                           @RequestParam(required = false) Long projectId,
                                           @RequestParam(required = false) Long counterpartyId,
                                           @ApiParam(value="yyyy-MM-dd HH:mm") @RequestParam(required = false) String dateAfter,
                                           @ApiParam(value="yyyy-MM-dd HH:mm") @RequestParam(required = false) String dateBefore, Principal principal) {
        Set<Transaction> fooSet = new LinkedHashSet<>(transactionService.getAll(isDeleted, principal.getName()));

        if (action != null)
            fooSet.retainAll(transactionService.getAllByAction(action));
        if (fromAccountId != null)
            fooSet.retainAll(transactionService.getAllByFromAccount(fromAccountId));
        if (categoryId != null)
            fooSet.retainAll(transactionService.getAllByCategory(categoryId));
        if (toAccountId != null)
            fooSet.retainAll(transactionService.getAllByToAccount(toAccountId));
        if (balanceLessThan != null)
            fooSet.retainAll(transactionService.getAllByBalanceLessThanEqual(balanceLessThan));
        if (balanceGreaterThan != null)
            fooSet.retainAll(transactionService.getAllByBalanceGreaterThanEqual(balanceGreaterThan));
        if (userId != null)
            fooSet.retainAll(transactionService.getAllByUserId(userId));
        if (projectId != null)
            fooSet.retainAll(transactionService.getAllByProject(projectId));
        if (counterpartyId != null)
            fooSet.retainAll(transactionService.getAllByCounterparty(counterpartyId));
        if (dateAfter != null)
            fooSet.retainAll(transactionService.getAllByDateCreatedAfter(dateAfter));
        if (dateBefore != null)
            fooSet.retainAll(transactionService.getAllByDateCreatedBefore(dateBefore));

        List<Transaction> list = new ArrayList<>(fooSet);
        return transactionService.getByPage(list, pageable);
    }

    @PostMapping("/addIncome")
    public ResponseEntity<Transaction> addIncome (@RequestBody TransactionIncomeDTO transactionIncomeDTO, Principal principal){
        return transactionService.addIncome(transactionIncomeDTO, principal.getName());
    }

    @GetMapping("/income")
    public ResponseEntity<Map<String,BigDecimal>> income (@RequestParam(required = false) boolean category,
                                                           @RequestParam(required = false) boolean project,
                                                           @RequestParam(required = false) boolean counterparty,
                                                           @ApiParam(value="yyyy-MM-dd HH:mm") @RequestParam String dateAfter,
                                                           @ApiParam(value="yyyy-MM-dd HH:mm") @RequestParam String dateBefore) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        if (category)
            return transactionService.incomeCategory(LocalDateTime.parse(dateAfter, formatter), LocalDateTime.parse(dateBefore, formatter));
        if (project)
            return transactionService.incomeProject(LocalDateTime.parse(dateAfter, formatter), LocalDateTime.parse(dateBefore, formatter));
        if (counterparty)
            return transactionService.incomeCounterparty(LocalDateTime.parse(dateAfter, formatter), LocalDateTime.parse(dateBefore, formatter));
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/expense")
    public ResponseEntity<Map<String,BigDecimal>> expense (@RequestParam(required = false) boolean category,
                                                           @RequestParam(required = false) boolean project,
                                                           @RequestParam(required = false) boolean counterparty,
                                                           @ApiParam(value="yyyy-MM-dd HH:mm") @RequestParam String dateAfter,
                                                           @ApiParam(value="yyyy-MM-dd HH:mm") @RequestParam String dateBefore) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        if (category)
            return transactionService.expenseCategory(LocalDateTime.parse(dateAfter, formatter), LocalDateTime.parse(dateBefore, formatter));
        if (project)
            return transactionService.expenseProject(LocalDateTime.parse(dateAfter, formatter), LocalDateTime.parse(dateBefore, formatter));
        if (counterparty)
            return transactionService.expenseCounterparty(LocalDateTime.parse(dateAfter, formatter), LocalDateTime.parse(dateBefore, formatter));
        return null;
    }

//    @GetMapping("/profit")
//    public ResponseEntity<BigDecimal> profit (@ApiParam(value="yyyy-MM-dd HH:mm") @RequestParam String dateAfter,
//                                              @ApiParam(value="yyyy-MM-dd HH:mm") @RequestParam String dateBefore, Principal principal) {
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
//        return transactionService.profit(LocalDateTime.parse(dateAfter, formatter), LocalDateTime.parse(dateBefore, formatter), principal.getName());
//    }

    @PostMapping("/addExpense")
    public ResponseEntity<Transaction> addExpense (@RequestBody TransactionExpenseDTO transactionExpenseDTO, Principal principal){
       return transactionService.addExpense(transactionExpenseDTO, principal.getName());
    }

    @PostMapping("/addRemittance")
    public ResponseEntity<Transaction> addRemittance (@RequestBody TransactionRemittanceDTO transactionRemittanceDTO, Principal principal){
        return transactionService.addRemittance(transactionRemittanceDTO, principal.getName());
    }

    @PutMapping("/updateIncome/{id}")
    public ResponseEntity<Transaction> updateIncome (@RequestBody TransactionIncomeDTO newTransaction, @PathVariable Long id, Principal principal) {
        return transactionService.updateIncomeById(newTransaction, id, principal.getName());
    }

    @PutMapping("/updateExpense/{id}")
    public ResponseEntity<Transaction> updateExpense (@RequestBody TransactionExpenseDTO newTransaction, @PathVariable Long id, Principal principal) {
        return transactionService.updateExpenseById(newTransaction, id, principal.getName());
    }

    @PutMapping("/updateRemittance/{id}")
    public ResponseEntity<Transaction> updateRemittance (@RequestBody TransactionRemittanceDTO newTransaction, @PathVariable Long id, Principal principal) {
        return transactionService.updateRemittanceById(newTransaction, id, principal.getName());
    }

    @DeleteMapping("/{id}")
    public ResponseMessage deleteTransaction (@PathVariable Long id, Principal principal) {
        return transactionService.deleteTransactionById(id, principal.getName());
    }

}
