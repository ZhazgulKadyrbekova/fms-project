package com.example.fms.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.fms.dto.UserAdminDTO;
import com.example.fms.dto.UserDTO;
import com.example.fms.dto.UserRegistrDTO;
import com.example.fms.entity.*;
import com.example.fms.exception.ResourceNotFoundException;
import com.example.fms.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private MailService mailService;
    @Autowired
    private PasswordEncoder encoder;
    @Autowired
    private JournalRepository journalRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ImageRepository imageRepository;
    @Autowired
    private DepartmentService departmentService;

    @Override
    public ResponseEntity<User> save(UserRegistrDTO userRegistrDTO) {
        User user = userRepository.findByEmail(userRegistrDTO.getEmail());
        if (user == null)
            throw  new ResourceNotFoundException("User email " + userRegistrDTO.getEmail() + " not found!");
        user.setPassword(encoder.encode(userRegistrDTO.getPassword()));
        user.setName(userRegistrDTO.getName());
        user.setSurname(userRegistrDTO.getSurname());
        userRepository.save(user);

        Journal journal = new Journal();
        journal.setTable("USER: " + user.getEmail());
        journal.setAction("registration");
        journal.setUser(user);
        journal.setDeleted(false);
        journalRepository.save(journal);

        return ResponseEntity.ok().body(user);
    }

    @Override
    public ResponseMessage createUser(UserDTO userDTO) {
        List<Department> departmentList = new ArrayList<>();
        for (Long depId : userDTO.getDepartmentList()){
            Department department = departmentService.getDepartmentById(depId).getBody();
            departmentList.add(department);
        }
        User user = new User();
        user.setEmail(userDTO.getEmail());
        user.setDepartments(departmentList);
        user.setActive(false);
        user.setActivationCode(UUID.randomUUID().toString());
        Role role = roleRepository.findByName("ROLE_USER");
        if (role == null)
            role = roleRepository.save(new Role("ROLE_USER"));
        user.setRole(role);
        user.setDeleted(false);

        String message = "Hello, ! \n" +
                " Please, visit next link to activate your account: http:neobis.herokuapp.com/registr/activate/" +
                user.getActivationCode();
        if(mailService.send(user.getEmail(), "Activation Code", message)){
            userRepository.save(user);
            return new ResponseMessage(HttpStatus.OK.value(), "Invitation sent successfully");
        }
        return new ResponseMessage(HttpStatus.BAD_GATEWAY.value(), "invitation was not sent");
    }

    @Override
    public void createAdmin(UserAdminDTO userAdminDTO) {
        User user = new User();
        user.setEmail(userAdminDTO.getEmail());
        user.setName(userAdminDTO.getName());
        user.setSurname(userAdminDTO.getSurname());
        user.setPassword(encoder.encode(userAdminDTO.getPassword()));
        user.setPosition(userAdminDTO.getPosition());
        user.setActive(true);
        user.setRole(roleRepository.findById(1L).orElse(roleRepository.save(new Role("ROLE_ADMIN"))));
        user.setDeleted(false);
        userRepository.save(user);
    }

    @Override
    public ResponseMessage activateUser(String code) {
        User user = userRepository.findByActivationCode(code);
        if (user == null) {
            return new ResponseMessage(HttpStatus.NOT_FOUND.value(), "could not activate user, " + code + " this code is not true");
        }
        user.setActivationCode(null);
        user.setActive(true);
        userRepository.save(user);
        return new ResponseMessage(HttpStatus.OK.value(), user.getEmail() + " successfully activated");
    }

    @Override
    public ResponseMessage sendForgotPassword(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null)
            return new ResponseMessage(HttpStatus.NOT_FOUND.value(), "User email " + email + " not found!");

        LocalDateTime localDateTime = LocalDateTime.now();
        String message = "Hello, ! \n" +
                " Please, visit next link to change your password: http:localhost:8080/registr/changeForgotPassword/" + localDateTime;
        if (!mailService.send(user.getEmail(), "Change password", message))
            return new ResponseMessage(HttpStatus.BAD_GATEWAY.value(), "smtp server failure, request was not sent");
        return new ResponseMessage(HttpStatus.OK.value(), "Successfully sent");
    }

    @Override
    public ResponseEntity<User> changeForgotPassword(String email, String newPassword, String localDateTime) {
        User user = userRepository.findByEmail(email);
        if (user == null)
            throw new ResourceNotFoundException("User email " + email + " not found!");

        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        LocalDateTime start = LocalDateTime.parse(localDateTime, formatter);
        LocalDateTime end = LocalDateTime.now();

        long minutes = ChronoUnit.MINUTES.between(start, end);

        if (minutes > 5)
            throw new ResourceNotFoundException("Link works only 5 minutes after sending");

        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);

        Journal journal = new Journal();
        journal.setTable("USER: " + user.getEmail());
        journal.setAction("changed password");
        journal.setUser(user);
        journal.setDeleted(false);
        journalRepository.save(journal);
        return ResponseEntity.ok().body(user);
    }

    @Override
    public ResponseEntity<User> changePassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email);
        if (user == null)
            throw new ResourceNotFoundException("User email " + email + " not found!");

        user.setPassword(encoder.encode(newPassword));
        userRepository.save(user);

        Journal journal = new Journal();
        journal.setTable("USER: " + user.getEmail());
        journal.setAction("changed password");
        journal.setUser(user);
        journal.setDeleted(false);
        journalRepository.save(journal);
        return ResponseEntity.ok().body(user);
    }

    @Override
    public List<User> getAll() {
       return userRepository.findAllByOrderByDateCreatedDesc();
    }

    @Override
    public Page<User> getByPage(List<User> list, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());

        List<User> output = new ArrayList<>();
        if (start <= end) {
            output = list.subList(start, end);
        }
        return new PageImpl<>(output, pageable, list.size());
    }

    @Override
    public List<User> getAllByPosition(String position) {
        return userRepository.findAllByPositionContainingIgnoringCase(position);
    }

    @Override
    public List<User> getAllByActive(boolean isActive) {
        return userRepository.findAllByActive(isActive);
    }

    @Override
    public List<User> getAllByDateCreatedAfter(String after) {
        //  String str = "2016-03-04 11:30";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime dateTime = LocalDateTime.parse(after, formatter);
        return userRepository.findAllByDateCreatedAfter(dateTime);
    }

    @Override
    public List<User> getAllByDateCreatedBefore(String before) {
        //  String str = "2016-03-04 11:30";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        LocalDateTime dateTime = LocalDateTime.parse(before, formatter);
        return userRepository.findAllByDateCreatedBefore(dateTime);
    }

    @Override
    public List<User> getAllByName(String name) {
        return userRepository.findAllByNameContainingIgnoringCase(name);
    }

    @Override
    public List<User> getAllBySurname(String surname) {
        return userRepository.findAllBySurnameContainingIgnoringCase(surname);
    }

    @Override
    public List<User> getAllByDepartments(List<Long> departmentIdList) {
        List<Department> departmentList = new ArrayList<>();
        for (Long depId : departmentIdList){
            Department department = departmentService.getDepartmentById(depId).getBody();
            departmentList.add(department);
        }

        List<User> userList = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            List<Department> depList = user.getDepartments();
            if (depList.containsAll(departmentList))
                userList.add(user);
        }
        return userList;
    }

    @Override
    public ResponseEntity<User> getByEmail(String email) {
        User user = userRepository.findByEmail(email);
        if (user == null)
            throw  new ResourceNotFoundException("User email " + email + " not found!");
        return ResponseEntity.ok().body(user);
    }

    @Override
    public ResponseEntity<User> getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(()-> new ResourceNotFoundException("User id " + id +" not found!"));
        return ResponseEntity.ok().body(user);
    }

    @Override
    public ResponseMessage blockUserById(Long id, String userEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User id " + id + " not found!"));
        user.setActive(false);
        userRepository.save(user);

        Journal journal = new Journal();
        journal.setTable("USER: " + user.getEmail());
        journal.setAction("block");
        journal.setUser(userRepository.findByEmail(userEmail));
        journal.setDeleted(false);
        journalRepository.save(journal);

        return new ResponseMessage(HttpStatus.OK.value(), "User successfully blocked");
    }

    @Override //for Init class
    public void createUser(User user) {
        user.setPassword(encoder.encode(user.getPassword()));
        userRepository.save(user);
    }

    @Override
    public ResponseEntity<User> setPosition(String position, String userEmail) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null)
            throw new ResourceNotFoundException("User email " + userEmail + " not found!");
        user.setPosition(position);
        userRepository.save(user);

        Journal journal = new Journal();
        journal.setTable("USER: " + user.getEmail());
        journal.setAction("changed position");
        journal.setUser(user);
        journal.setDeleted(false);
        journalRepository.save(journal);

        return ResponseEntity.ok().body(user);
    }

    @Override
    public ResponseEntity<User> setImage(MultipartFile multipartFile, String userEmail) throws IOException {

        final String urlKey = "cloudinary://119264965729773:1qhca12iztxCm0Df0nSBYtsIRF4@bagdash/"; //в конце добавляем '/'
        Image image = new Image();
        File file;
        try{
            file = Files.createTempFile(System.currentTimeMillis() + "",
                    multipartFile.getOriginalFilename().substring(multipartFile.getOriginalFilename().length()-4)) // .jpg
                    .toFile();
            multipartFile.transferTo(file);

            Cloudinary cloudinary = new Cloudinary(urlKey);
            Map uploadResult = cloudinary.uploader().upload(file, ObjectUtils.emptyMap());
            image.setName((String) uploadResult.get("public_id"));
            image.setUrl((String) uploadResult.get("url"));
            image.setFormat((String) uploadResult.get("format"));
            imageRepository.save(image);

            User user = userRepository.findByEmail(userEmail);
            user.setImage(image);
            userRepository.save(user);

            Journal journal = new Journal();
            journal.setTable("USER: " + user.getEmail());
            journal.setAction("set image");
            journal.setUser(user);
            journal.setDeleted(false);
            journalRepository.save(journal);

            return ResponseEntity.ok().body(user);
        }catch (IOException e){
            throw new IOException("User was unable to set a image");
        }
    }

    @Override
    public ResponseMessage deleteImage(String email) {
        User user = userRepository.findByEmail(email);
        user.setImage(null);
        userRepository.save(user);

        Journal journal = new Journal();
        journal.setTable("USER: " + user.getEmail());
        journal.setAction("delete the image");
        journal.setUser(user);
        journal.setDeleted(false);
        journalRepository.save(journal);

        return new ResponseMessage(HttpStatus.OK.value(), "image successfully deleted");
    }

    @Override
    public ResponseEntity<User> setDepartmentList(List<Long> departmentIdList, String userEmail, String admin) {
        User user = userRepository.findByEmail(userEmail);
        if (user == null)
            throw new ResourceNotFoundException(userEmail + " user with this email not found!");

        List<Department> departmentList = new ArrayList<>();
        for (Long depId : departmentIdList){
            Department department = departmentService.getDepartmentById(depId).getBody();
            departmentList.add(department);
        }
        user.setDepartments(departmentList);

        Journal journal = new Journal();
        journal.setTable("USER: " + user.getEmail());
        journal.setAction("set department");
        journal.setUser(userRepository.findByEmail(admin));
        journal.setDeleted(false);
        journalRepository.save(journal);

        return ResponseEntity.ok().body(userRepository.save(user));
    }

    @Override
    public ResponseEntity<User> unBlockUserById(Long id, String userEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User id " + id + " not found!"));
        user.setActive(true);
        userRepository.save(user);

        Journal journal = new Journal();
        journal.setTable("USER: " + user.getEmail());
        journal.setAction("unBlock");
        journal.setUser(userRepository.findByEmail(userEmail));
        journal.setDeleted(false);
        journalRepository.save(journal);

        return ResponseEntity.ok().body(user);
    }
}