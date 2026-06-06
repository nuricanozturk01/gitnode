import { NgModule, Optional } from '@angular/core';
import { LucideAngularComponent } from './lucide-angular.component';
import { LUCIDE_ICONS, LucideIconProvider } from './lucide-icon.provider';
import { Icons } from './icons.provider';
import * as i0 from "@angular/core";
const legacyIconProviderFactory = (icons) => {
    return new LucideIconProvider(icons ?? {});
};
export class LucideAngularModule {
    static pick(icons) {
        return {
            ngModule: LucideAngularModule,
            providers: [
                { provide: LUCIDE_ICONS, multi: true, useValue: new LucideIconProvider(icons) },
                {
                    provide: LUCIDE_ICONS,
                    multi: true,
                    useFactory: legacyIconProviderFactory,
                    deps: [[new Optional(), Icons]],
                },
            ],
        };
    }
}
LucideAngularModule.ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "13.3.12", ngImport: i0, type: LucideAngularModule, deps: [], target: i0.ɵɵFactoryTarget.NgModule });
LucideAngularModule.ɵmod = i0.ɵɵngDeclareNgModule({ minVersion: "12.0.0", version: "13.3.12", ngImport: i0, type: LucideAngularModule, declarations: [LucideAngularComponent], exports: [LucideAngularComponent] });
LucideAngularModule.ɵinj = i0.ɵɵngDeclareInjector({ minVersion: "12.0.0", version: "13.3.12", ngImport: i0, type: LucideAngularModule, imports: [[]] });
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "13.3.12", ngImport: i0, type: LucideAngularModule, decorators: [{
            type: NgModule,
            args: [{
                    declarations: [LucideAngularComponent],
                    imports: [],
                    exports: [LucideAngularComponent],
                }]
        }] });
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoibHVjaWRlLWFuZ3VsYXIubW9kdWxlLmpzIiwic291cmNlUm9vdCI6IiIsInNvdXJjZXMiOlsiLi4vLi4vLi4vc3JjL2xpYi9sdWNpZGUtYW5ndWxhci5tb2R1bGUudHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6IkFBQUEsT0FBTyxFQUF1QixRQUFRLEVBQUUsUUFBUSxFQUFFLE1BQU0sZUFBZSxDQUFDO0FBQ3hFLE9BQU8sRUFBRSxzQkFBc0IsRUFBRSxNQUFNLDRCQUE0QixDQUFDO0FBRXBFLE9BQU8sRUFBRSxZQUFZLEVBQUUsa0JBQWtCLEVBQUUsTUFBTSx3QkFBd0IsQ0FBQztBQUMxRSxPQUFPLEVBQUUsS0FBSyxFQUFFLE1BQU0sa0JBQWtCLENBQUM7O0FBRXpDLE1BQU0seUJBQXlCLEdBQUcsQ0FBQyxLQUFtQixFQUFFLEVBQUU7SUFDeEQsT0FBTyxJQUFJLGtCQUFrQixDQUFDLEtBQUssSUFBSSxFQUFFLENBQUMsQ0FBQztBQUM3QyxDQUFDLENBQUM7QUFPRixNQUFNLE9BQU8sbUJBQW1CO0lBQzlCLE1BQU0sQ0FBQyxJQUFJLENBQUMsS0FBa0I7UUFDNUIsT0FBTztZQUNMLFFBQVEsRUFBRSxtQkFBbUI7WUFDN0IsU0FBUyxFQUFFO2dCQUNULEVBQUUsT0FBTyxFQUFFLFlBQVksRUFBRSxLQUFLLEVBQUUsSUFBSSxFQUFFLFFBQVEsRUFBRSxJQUFJLGtCQUFrQixDQUFDLEtBQUssQ0FBQyxFQUFFO2dCQUMvRTtvQkFDRSxPQUFPLEVBQUUsWUFBWTtvQkFDckIsS0FBSyxFQUFFLElBQUk7b0JBQ1gsVUFBVSxFQUFFLHlCQUF5QjtvQkFDckMsSUFBSSxFQUFFLENBQUMsQ0FBQyxJQUFJLFFBQVEsRUFBRSxFQUFFLEtBQUssQ0FBQyxDQUFDO2lCQUNoQzthQUNGO1NBQ0YsQ0FBQztJQUNKLENBQUM7O2lIQWRVLG1CQUFtQjtrSEFBbkIsbUJBQW1CLGlCQUpmLHNCQUFzQixhQUUzQixzQkFBc0I7a0hBRXJCLG1CQUFtQixZQUhyQixFQUFFOzRGQUdBLG1CQUFtQjtrQkFML0IsUUFBUTttQkFBQztvQkFDUixZQUFZLEVBQUUsQ0FBQyxzQkFBc0IsQ0FBQztvQkFDdEMsT0FBTyxFQUFFLEVBQUU7b0JBQ1gsT0FBTyxFQUFFLENBQUMsc0JBQXNCLENBQUM7aUJBQ2xDIiwic291cmNlc0NvbnRlbnQiOlsiaW1wb3J0IHsgTW9kdWxlV2l0aFByb3ZpZGVycywgTmdNb2R1bGUsIE9wdGlvbmFsIH0gZnJvbSAnQGFuZ3VsYXIvY29yZSc7XG5pbXBvcnQgeyBMdWNpZGVBbmd1bGFyQ29tcG9uZW50IH0gZnJvbSAnLi9sdWNpZGUtYW5ndWxhci5jb21wb25lbnQnO1xuaW1wb3J0IHsgTHVjaWRlSWNvbnMgfSBmcm9tICcuLi9pY29ucy90eXBlcyc7XG5pbXBvcnQgeyBMVUNJREVfSUNPTlMsIEx1Y2lkZUljb25Qcm92aWRlciB9IGZyb20gJy4vbHVjaWRlLWljb24ucHJvdmlkZXInO1xuaW1wb3J0IHsgSWNvbnMgfSBmcm9tICcuL2ljb25zLnByb3ZpZGVyJztcblxuY29uc3QgbGVnYWN5SWNvblByb3ZpZGVyRmFjdG9yeSA9IChpY29ucz86IEx1Y2lkZUljb25zKSA9PiB7XG4gIHJldHVybiBuZXcgTHVjaWRlSWNvblByb3ZpZGVyKGljb25zID8/IHt9KTtcbn07XG5cbkBOZ01vZHVsZSh7XG4gIGRlY2xhcmF0aW9uczogW0x1Y2lkZUFuZ3VsYXJDb21wb25lbnRdLFxuICBpbXBvcnRzOiBbXSxcbiAgZXhwb3J0czogW0x1Y2lkZUFuZ3VsYXJDb21wb25lbnRdLFxufSlcbmV4cG9ydCBjbGFzcyBMdWNpZGVBbmd1bGFyTW9kdWxlIHtcbiAgc3RhdGljIHBpY2soaWNvbnM6IEx1Y2lkZUljb25zKTogTW9kdWxlV2l0aFByb3ZpZGVyczxMdWNpZGVBbmd1bGFyTW9kdWxlPiB7XG4gICAgcmV0dXJuIHtcbiAgICAgIG5nTW9kdWxlOiBMdWNpZGVBbmd1bGFyTW9kdWxlLFxuICAgICAgcHJvdmlkZXJzOiBbXG4gICAgICAgIHsgcHJvdmlkZTogTFVDSURFX0lDT05TLCBtdWx0aTogdHJ1ZSwgdXNlVmFsdWU6IG5ldyBMdWNpZGVJY29uUHJvdmlkZXIoaWNvbnMpIH0sXG4gICAgICAgIHtcbiAgICAgICAgICBwcm92aWRlOiBMVUNJREVfSUNPTlMsXG4gICAgICAgICAgbXVsdGk6IHRydWUsXG4gICAgICAgICAgdXNlRmFjdG9yeTogbGVnYWN5SWNvblByb3ZpZGVyRmFjdG9yeSxcbiAgICAgICAgICBkZXBzOiBbW25ldyBPcHRpb25hbCgpLCBJY29uc11dLFxuICAgICAgICB9LFxuICAgICAgXSxcbiAgICB9O1xuICB9XG59XG4iXX0=