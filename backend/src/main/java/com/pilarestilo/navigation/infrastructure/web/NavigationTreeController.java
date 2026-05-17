package com.pilarestilo.navigation.infrastructure.web;

import com.pilarestilo.navigation.application.dto.NavigationTreeDto;
import com.pilarestilo.navigation.application.usecases.GetNavigationTreeUseCase;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/navigation")
public class NavigationTreeController {

    private final GetNavigationTreeUseCase getNavigationTree;

    public NavigationTreeController(GetNavigationTreeUseCase getNavigationTree) {
        this.getNavigationTree = getNavigationTree;
    }

    @GetMapping("/tree")
    public NavigationTreeDto tree(@RequestParam(defaultValue = "es") String locale) {
        return getNavigationTree.execute(locale);
    }
}
