package com.pilarestilo.varianttemplate.infrastructure.web.controllers;

import com.pilarestilo.varianttemplate.application.dto.VariantTemplateDto;
import com.pilarestilo.varianttemplate.application.usecases.CreateVariantTemplateUseCase;
import com.pilarestilo.varianttemplate.application.usecases.DeleteVariantTemplateUseCase;
import com.pilarestilo.varianttemplate.application.usecases.GetVariantTemplateUseCase;
import com.pilarestilo.varianttemplate.application.usecases.ListVariantTemplatesUseCase;
import com.pilarestilo.varianttemplate.application.usecases.UpdateVariantTemplateUseCase;
import com.pilarestilo.varianttemplate.infrastructure.web.requests.CreateVariantTemplateRequest;
import com.pilarestilo.varianttemplate.infrastructure.web.requests.UpdateVariantTemplateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Every method requires ADMIN, unlike CategoryController -- the template catalogue has no
 * storefront or non-admin consumer (customers only ever see a product's already-resolved
 * variantFieldConfig, a separate field on ProductDto).
 */
@RestController
@RequestMapping("/api/variant-templates")
public class VariantTemplateController {

    private final CreateVariantTemplateUseCase createVariantTemplate;
    private final UpdateVariantTemplateUseCase updateVariantTemplate;
    private final DeleteVariantTemplateUseCase deleteVariantTemplate;
    private final GetVariantTemplateUseCase getVariantTemplate;
    private final ListVariantTemplatesUseCase listVariantTemplates;

    public VariantTemplateController(CreateVariantTemplateUseCase createVariantTemplate,
                                      UpdateVariantTemplateUseCase updateVariantTemplate,
                                      DeleteVariantTemplateUseCase deleteVariantTemplate,
                                      GetVariantTemplateUseCase getVariantTemplate,
                                      ListVariantTemplatesUseCase listVariantTemplates) {
        this.createVariantTemplate = createVariantTemplate;
        this.updateVariantTemplate = updateVariantTemplate;
        this.deleteVariantTemplate = deleteVariantTemplate;
        this.getVariantTemplate = getVariantTemplate;
        this.listVariantTemplates = listVariantTemplates;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<VariantTemplateDto> list() {
        return listVariantTemplates.execute();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/{id}")
    public VariantTemplateDto getById(@PathVariable UUID id) {
        return getVariantTemplate.execute(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<VariantTemplateDto> create(@Valid @RequestBody CreateVariantTemplateRequest req) {
        VariantTemplateDto dto = createVariantTemplate.execute(req.name(), req.primary(), req.secondary());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public VariantTemplateDto update(@PathVariable UUID id, @Valid @RequestBody UpdateVariantTemplateRequest req) {
        return updateVariantTemplate.execute(id, req.name(), req.primary(), req.secondary());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteVariantTemplate.execute(id);
        return ResponseEntity.noContent().build();
    }
}
