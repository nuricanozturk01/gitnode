import { ChangeDetectorRef, Component, ElementRef, Inject, Input, Renderer2, } from '@angular/core';
import defaultAttributes from '../icons/constants/default-attributes';
import { LUCIDE_ICONS } from './lucide-icon.provider';
import { LucideIconConfig } from './lucide-icon.config';
import { hasA11yProp } from '../utils/hasA11yProp';
import * as i0 from "@angular/core";
import * as i1 from "./lucide-icon.config";
export function formatFixed(number, decimals = 3) {
    return parseFloat(number.toFixed(decimals)).toString(10);
}
export class LucideAngularComponent {
    constructor(elem, renderer, changeDetector, iconProviders, iconConfig) {
        this.elem = elem;
        this.renderer = renderer;
        this.changeDetector = changeDetector;
        this.iconProviders = iconProviders;
        this.iconConfig = iconConfig;
        this.absoluteStrokeWidth = false;
        this.defaultSize = defaultAttributes.height;
    }
    get size() {
        return this._size ?? this.iconConfig.size;
    }
    set size(value) {
        if (value) {
            this._size = this.parseNumber(value);
        }
        else {
            delete this._size;
        }
    }
    get strokeWidth() {
        return this._strokeWidth ?? this.iconConfig.strokeWidth;
    }
    set strokeWidth(value) {
        if (value) {
            this._strokeWidth = this.parseNumber(value);
        }
        else {
            delete this._strokeWidth;
        }
    }
    ngOnChanges(changes) {
        if (changes.name ||
            changes.img ||
            changes.color ||
            changes.size ||
            changes.absoluteStrokeWidth ||
            changes.strokeWidth ||
            changes.class) {
            this.color = this.color ?? this.iconConfig.color;
            this.size = this.parseNumber(this.size ?? this.iconConfig.size);
            this.strokeWidth = this.parseNumber(this.strokeWidth ?? this.iconConfig.strokeWidth);
            this.absoluteStrokeWidth = this.absoluteStrokeWidth ?? this.iconConfig.absoluteStrokeWidth;
            const nameOrIcon = this.img ?? this.name;
            const restAttributes = this.getRestAttributes();
            if (!hasA11yProp(restAttributes)) {
                this.renderer.setAttribute(this.elem.nativeElement, 'aria-hidden', 'true');
            }
            if (typeof nameOrIcon === 'string') {
                const icoOfName = this.getIcon(this.toPascalCase(nameOrIcon));
                if (icoOfName) {
                    this.replaceElement(icoOfName);
                }
                else {
                    throw new Error(`The "${nameOrIcon}" icon has not been provided by any available icon providers.`);
                }
            }
            else if (Array.isArray(nameOrIcon)) {
                this.replaceElement(nameOrIcon);
            }
            else {
                throw new Error(`No icon name or image has been provided.`);
            }
        }
        this.changeDetector.markForCheck();
    }
    replaceElement(img) {
        const childElements = this.elem.nativeElement.childNodes;
        const attributes = {
            ...defaultAttributes,
            width: this.size,
            height: this.size,
            stroke: this.color ?? this.iconConfig.color,
            'stroke-width': this.absoluteStrokeWidth
                ? formatFixed(this.strokeWidth / (this.size / this.defaultSize))
                : this.strokeWidth.toString(10),
        };
        const icoElement = this.createElement(['svg', attributes, img]);
        icoElement.classList.add('lucide');
        if (typeof this.name === 'string') {
            icoElement.classList.add(`lucide-${this.name.replace('_', '-')}`);
        }
        if (this.class) {
            icoElement.classList.add(...this.class
                .split(/ /)
                .map((a) => a.trim())
                .filter((a) => a.length > 0));
        }
        for (const child of childElements) {
            this.renderer.removeChild(this.elem.nativeElement, child);
        }
        this.renderer.appendChild(this.elem.nativeElement, icoElement);
    }
    getRestAttributes() {
        const restAttributeMap = this.elem.nativeElement.attributes;
        const restAttributes = Object.fromEntries(Array.from(restAttributeMap).map((item) => [item.name, item.value]));
        return restAttributes;
    }
    toPascalCase(str) {
        return str.replace(/(\w)([a-z0-9]*)(_|-|\s*)/g, (g0, g1, g2) => g1.toUpperCase() + g2.toLowerCase());
    }
    parseNumber(value) {
        if (typeof value === 'string') {
            const parsedValue = parseInt(value, 10);
            if (isNaN(parsedValue)) {
                throw new Error(`${value} is not numeric.`);
            }
            return parsedValue;
        }
        return value;
    }
    getIcon(name) {
        for (const iconProvider of Array.isArray(this.iconProviders)
            ? this.iconProviders
            : [this.iconProviders]) {
            if (iconProvider.hasIcon(name)) {
                return iconProvider.getIcon(name);
            }
        }
        return null;
    }
    createElement([tag, attrs, children = []]) {
        const element = this.renderer.createElement(tag, 'http://www.w3.org/2000/svg');
        Object.keys(attrs).forEach((name) => {
            const attrValue = typeof attrs[name] === 'string' ? attrs[name] : attrs[name].toString(10);
            this.renderer.setAttribute(element, name, attrValue);
        });
        if (children.length) {
            children.forEach((child) => {
                const childElement = this.createElement(child);
                this.renderer.appendChild(element, childElement);
            });
        }
        return element;
    }
}
LucideAngularComponent.ɵfac = i0.ɵɵngDeclareFactory({ minVersion: "12.0.0", version: "13.3.12", ngImport: i0, type: LucideAngularComponent, deps: [{ token: ElementRef }, { token: Renderer2 }, { token: ChangeDetectorRef }, { token: LUCIDE_ICONS }, { token: LucideIconConfig }], target: i0.ɵɵFactoryTarget.Component });
LucideAngularComponent.ɵcmp = i0.ɵɵngDeclareComponent({ minVersion: "12.0.0", version: "13.3.12", type: LucideAngularComponent, selector: "lucide-angular, lucide-icon, i-lucide, span-lucide", inputs: { class: "class", name: "name", img: "img", color: "color", absoluteStrokeWidth: "absoluteStrokeWidth", size: "size", strokeWidth: "strokeWidth" }, usesOnChanges: true, ngImport: i0, template: '<ng-content></ng-content>', isInline: true });
i0.ɵɵngDeclareClassMetadata({ minVersion: "12.0.0", version: "13.3.12", ngImport: i0, type: LucideAngularComponent, decorators: [{
            type: Component,
            args: [{
                    selector: 'lucide-angular, lucide-icon, i-lucide, span-lucide',
                    template: '<ng-content></ng-content>',
                }]
        }], ctorParameters: function () { return [{ type: i0.ElementRef, decorators: [{
                    type: Inject,
                    args: [ElementRef]
                }] }, { type: i0.Renderer2, decorators: [{
                    type: Inject,
                    args: [Renderer2]
                }] }, { type: i0.ChangeDetectorRef, decorators: [{
                    type: Inject,
                    args: [ChangeDetectorRef]
                }] }, { type: undefined, decorators: [{
                    type: Inject,
                    args: [LUCIDE_ICONS]
                }] }, { type: i1.LucideIconConfig, decorators: [{
                    type: Inject,
                    args: [LucideIconConfig]
                }] }]; }, propDecorators: { class: [{
                type: Input
            }], name: [{
                type: Input
            }], img: [{
                type: Input
            }], color: [{
                type: Input
            }], absoluteStrokeWidth: [{
                type: Input
            }], size: [{
                type: Input
            }], strokeWidth: [{
                type: Input
            }] } });
//# sourceMappingURL=data:application/json;base64,eyJ2ZXJzaW9uIjozLCJmaWxlIjoibHVjaWRlLWFuZ3VsYXIuY29tcG9uZW50LmpzIiwic291cmNlUm9vdCI6IiIsInNvdXJjZXMiOlsiLi4vLi4vLi4vLi4vLi4vc3JjL2xpYi9sdWNpZGUtYW5ndWxhci5jb21wb25lbnQudHMiXSwibmFtZXMiOltdLCJtYXBwaW5ncyI6IkFBQUEsT0FBTyxFQUNMLGlCQUFpQixFQUNqQixTQUFTLEVBQ1QsVUFBVSxFQUNWLE1BQU0sRUFDTixLQUFLLEVBRUwsU0FBUyxHQUVWLE1BQU0sZUFBZSxDQUFDO0FBRXZCLE9BQU8saUJBQWlCLE1BQU0sdUNBQXVDLENBQUM7QUFDdEUsT0FBTyxFQUFFLFlBQVksRUFBK0IsTUFBTSx3QkFBd0IsQ0FBQztBQUNuRixPQUFPLEVBQUUsZ0JBQWdCLEVBQUUsTUFBTSxzQkFBc0IsQ0FBQztBQUN4RCxPQUFPLEVBQUUsV0FBVyxFQUFFLE1BQU0sc0JBQXNCLENBQUM7OztBQW1CbkQsTUFBTSxVQUFVLFdBQVcsQ0FBQyxNQUFjLEVBQUUsUUFBUSxHQUFHLENBQUM7SUFDdEQsT0FBTyxVQUFVLENBQUMsTUFBTSxDQUFDLE9BQU8sQ0FBQyxRQUFRLENBQUMsQ0FBQyxDQUFDLFFBQVEsQ0FBQyxFQUFFLENBQUMsQ0FBQztBQUMzRCxDQUFDO0FBTUQsTUFBTSxPQUFPLHNCQUFzQjtJQVFqQyxZQUM4QixJQUFnQixFQUNqQixRQUFtQixFQUNYLGNBQWlDLEVBQ3RDLGFBQTRDLEVBQ3hDLFVBQTRCO1FBSmxDLFNBQUksR0FBSixJQUFJLENBQVk7UUFDakIsYUFBUSxHQUFSLFFBQVEsQ0FBVztRQUNYLG1CQUFjLEdBQWQsY0FBYyxDQUFtQjtRQUN0QyxrQkFBYSxHQUFiLGFBQWEsQ0FBK0I7UUFDeEMsZUFBVSxHQUFWLFVBQVUsQ0FBa0I7UUFSdkQsd0JBQW1CLEdBQUcsS0FBSyxDQUFDO1FBVW5DLElBQUksQ0FBQyxXQUFXLEdBQUcsaUJBQWlCLENBQUMsTUFBTSxDQUFDO0lBQzlDLENBQUM7SUFJRCxJQUFJLElBQUk7UUFDTixPQUFPLElB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QVksR0FBRyxJQUFJLENBQUMsYUFBYSxDQUFDLEtBQUssQ0FBQyxDQUFDO2dCQUMvQyxJQUFJLENBQUMsUUFBUSxDQUFDLFdBQVcsQ0FBQyxPQUFPLEVBQUUsWUFBWSxDQUFDLENBQUM7WUFDbkQsQ0FBQyxDQUFDLENBQUM7U0FDSjtRQUVELE9BQU8sT0FBTyxDQUFDO0lBQ2pCLENBQUM7O29IQWpMVSxzQkFBc0Isa0JBU3ZCLFVBQVUsYUFDVixTQUFTLGFBQ1QsaUJBQWlCLGFBQ2pCLFlBQVksYUFDWixnQkFBZ0I7d0dBYmYsc0JBQXNCLDJRQUZ2QiwyQkFBMkI7NEZBRTFCLHNCQUFzQjtrQkFKbEMsU0FBUzttQkFBQztvQkFDVCxRQUFRLEVBQUUsb0RBQW9EO29CQUM5RCxRQUFRLEVBQUUsMkJBQTJCO2lCQUN0Qzs7MEJBVUksTUFBTTsyQkFBQyxVQUFVOzswQkFDakIsTUFBTTsyQkFBQyxTQUFTOzswQkFDaEIsTUFBTTsyQkFBQyxpQkFBaUI7OzBCQUN4QixNQUFNOzJCQUFDLFlBQVk7OzBCQUNuQixNQUFNOzJCQUFDLGdCQUFnQjs0Q0FaakIsS0FBSztzQkFBYixLQUFLO2dCQUNHLElBQUk7c0JBQVosS0FBSztnQkFDRyxHQUFHO3NCQUFYLEtBQUs7Z0JBQ0csS0FBSztzQkFBYixLQUFLO2dCQUNHLG1CQUFtQjtzQkFBM0IsS0FBSztnQkFtQk8sSUFBSTtzQkFBaEIsS0FBSztnQkFjTyxXQUFXO3NCQUF2QixLQUFLIiwic291cmNlc0NvbnRlbnQiOlsiaW1wb3J0IHtcbiAgQ2hhbmdlRGV0ZWN0b3JSZWYsXG4gIENvbXBvbmVudCxcbiAgRWxlbWVudFJlZixcbiAgSW5qZWN0LFxuICBJbnB1dCxcbiAgT25DaGFuZ2VzLFxuICBSZW5kZXJlcjIsXG4gIFNpbXBsZUNoYW5nZSxcbn0gZnJvbSAnQGFuZ3VsYXIvY29yZSc7XG5pbXBvcnQgeyBMdWNpZGVJY29uRGF0YSB9IGZyb20gJy4uL2ljb25zL3R5cGVzJztcbmltcG9ydCBkZWZhdWx0QXR0cmlidXRlcyBmcm9tICcuLi9pY29ucy9jb25zdGFudHMvZGVmYXVsdC1hdHRyaWJ1dGVzJztcbmltcG9ydCB7IExVQ0lERV9JQ09OUywgTHVjaWRlSWNvblByb3ZpZGVySW50ZXJmYWNlIH0gZnJvbSAnLi9sdWNpZGUtaWNvbi5wcm92aWRlcic7XG5pbXBvcnQgeyBMdWNpZGVJY29uQ29uZmlnIH0gZnJvbSAnLi9sdWNpZGUtaWNvbi5jb25maWcnO1xuaW1wb3J0IHsgaGFzQTExeVByb3AgfSBmcm9tICcuLi91dGlscy9oYXNBMTF5UHJvcCc7XG5cbmludGVyZmFjZSBUeXBlZENoYW5nZTxUPiBleHRlbmRzIFNpbXBsZUNoYW5nZSB7XG4gIHByZXZpb3VzVmFsdWU6IFQ7XG4gIGN1cnJlbnRWYWx1ZTogVDtcbn1cblxudHlwZSBTdmdBdHRyaWJ1dGVzID0geyBba2V5OiBzdHJpbmddOiBzdHJpbmcgfCBudW1iZXIgfTtcblxudHlwZSBMdWNpZGVBbmd1bGFyQ29tcG9uZW50Q2hhbmdlcyA9IHtcbiAgbmFtZT86IFR5cGVkQ2hhbmdlPHN0cmluZyB8IEx1Y2lkZUljb25EYXRhPjtcbiAgaW1nPzogVHlwZWRDaGFuZ2U8THVjaWRlSWNvbkRhdGEgfCB1bmRlZmluZWQ+O1xuICBjb2xvcj86IFR5cGVkQ2hhbmdlPHN0cmluZz47XG4gIHNpemU/OiBUeXBlZENoYW5nZTxudW1iZXI+O1xuICBzdHJva2VXaWR0aD86IFR5cGVkQ2hhbmdlPG51bWJlcj47XG4gIGFic29sdXRlU3Ryb2tlV2lkdGg/OiBUeXBlZENoYW5nZTxib29sZWFuPjtcbiAgY2xhc3M6IFR5cGVkQ2hhbmdlPHN0cmluZz47XG59O1xuXG5leHBvcnQgZnVuY3Rpb24gZm9ybWF0Rml4ZWQobnVtYmVyOiBudW1iZXIsIGRlY2ltYWxzID0gMyk6IHN0cmluZyB7XG4gIHJldHVybiBwYXJzZUZsb2F0KG51bWJlci50b0ZpeGVkKGRlY2ltYWxzKSkudG9TdHJpbmcoMTApO1xufVxuXG5AQ29tcG9uZW50KHtcbiAgc2VsZWN0b3I6ICdsdWNpZGUtYW5ndWxhciwgbHVjaWRlLWljb24sIGktbHVjaWRlLCBzcGFuLWx1Y2lkZScsXG4gIHRlbXBsYXRlOiAnPG5nLWNvbnRlbnQ+PC9uZy1jb250ZW50PicsXG59KVxuZXhwb3J0IGNsYXNzIEx1Y2lkZUFuZ3VsYXJDb21wb25lbnQgaW1wbGVtZW50cyBPbkNoYW5nZXMge1xuICBASW5wdXQoKSBjbGFzcz86IHN0cmluZztcbiAgQElucHV0KCkgbmFtZT86IHN0cmluZyB8IEx1Y2lkZUljb25EYXRhO1xuICBASW5wdXQoKSBpbWc/OiBMdWNpZGVJY29uRGF0YTtcbiAgQElucHV0KCkgY29sb3I/OiBzdHJpbmc7XG4gIEBJbnB1dCgpIGFic29sdXRlU3Ryb2tlV2lkdGggPSBmYWxzZTtcbiAgZGVmYXVsdFNpemU6IG51bWJlcjtcblxuICBjb25zdHJ1Y3RvcihcbiAgICBASW5qZWN0KEVsZW1lbnRSZWYpIHByaXZhdGUgZWxlbTogRWxlbWVudFJlZixcbiAgICBASW5qZWN0KFJlbmRlcmVyMikgcHJpdmF0ZSByZW5kZXJlcjogUmVuZGVyZXIyLFxuICAgIEBJbmplY3QoQ2hhbmdlRGV0ZWN0b3JSZWYpIHByaXZhdGUgY2hhbmdlRGV0ZWN0b3I6IENoYW5nZURldGVjdG9yUmVmLFxuICAgIEBJbmplY3QoTFVDSURFX0lDT05TKSBwcml2YXRlIGljb25Qcm92aWRlcnM6IEx1Y2lkZUljb25Qcm92aWRlckludGVyZmFjZVtdLFxuICAgIEBJbmplY3QoTHVjaWRlSWNvbkNvbmZpZykgcHJpdmF0ZSBpY29uQ29uZmlnOiBMdWNpZGVJY29uQ29uZmlnLFxuICApIHtcbiAgICB0aGlzLmRlZmF1bHRTaXplID0gZGVmYXVsdEF0dHJpYnV0ZXMuaGVpZ2h0O1xuICB9XG5cbiAgX3NpemU/OiBudW1iZXI7XG5cbiAgZ2V0IHNpemUoKTogbnVtYmVyIHtcbiAgICByZXR1cm4gdGhpcy5fc2l6ZSA/PyB0aGlzLmljb25Db25maWcuc2l6ZTtcbiAgfVxuXG4gIEBJbnB1dCgpIHNldCBzaXplKHZhbHVlOiBzdHJpbmcgfCBudW1iZXIgfCB1bmRlZmluZWQpIHtcbiAgICBpZiAodmFsdWUpIHtcbiAgICAgIHRoaXMuX3NpemUgPSB0aGlzLnBhcnNlTnVtYmVyKHZhbHVlKTtcbiAgICB9IGVsc2Uge1xuICAgICAgZGVsZXRlIHRoaXMuX3NpemU7XG4gICAgfVxuICB9XG5cbiAgX3N0cm9rZVdpZHRoPzogbnVtYmVyO1xuXG4gIGdldCBzdHJva2VXaWR0aCgpOiBudW1iZXIge1xuICAgIHJldHVybiB0aGlzLl9zdHJva2VXaWR0aCA/PyB0aGlzLmljb25Db25maWcuc3Ryb2tlV2lkdGg7XG4gIH1cblxuICBASW5wdXQoKSBzZXQgc3Ryb2tlV2lkdGgodmFsdWU6IHN0cmluZyB8IG51bWJlciB8IHVuZGVmaW5lZCkge1xuICAgIGlmICh2YWx1ZSkge1xuICAgICAgdGhpcy5fc3Ryb2tlV2lkdGggPSB0aGlzLnBhcnNlTnVtYmVyKHZhbHVlKTtcbiAgICB9IGVsc2Uge1xuICAgICAgZGVsZXRlIHRoaXMuX3N0cm9rZVdpZHRoO1xuICAgIH1cbiAgfVxuXG4gIG5nT25DaGFuZ2VzKGNoYW5nZXM6IEx1Y2lkZUFuZ3VsYXJDb21wb25lbnRDaGFuZ2VzKTogdm9pZCB7XG4gICAgaWYgKFxuICAgICAgY2hhbmdlcy5uYW1lIHx8XG4gICAgICBjaGFuZ2VzLmltZyB8fFxuICAgICAgY2hhbmdlcy5jb2xvciB8fFxuICAgICAgY2hhbmdlcy5zaXplIHx8XG4gICAgICBjaGFuZ2VzLmFic29sdXRlU3Ryb2tlV2lkdGggfHxcbiAgICAgIGNoYW5nZXMuc3Ryb2tlV2lkdGggfHxcbiAgICAgIGNoYW5nZXMuY2xhc3NcbiAgICApIHtcbiAgICAgIHRoaXMuY29sb3IgPSB0aGlzLmNvbG9yID8/IHRoaXMuaWNvbkNvbmZpZy5jb2xvcjtcbiAgICAgIHRoaXMuc2l6ZSA9IHRoaXMucGFyc2VOdW1iZXIodGhpcy5zaXplID8/IHRoaXMuaWNvbkNvbmZpZy5zaXplKTtcbiAgICAgIHRoaXMuc3Ryb2tlV2lkdGggPSB0aGlzLnBhcnNlTnVtYmVyKHRoaXMuc3Ryb2tlV2lkdGggPz8gdGhpcy5pY29uQ29uZmlnLnN0cm9rZVdpZHRoKTtcbiAgICAgIHRoaXMuYWJzb2x1dGVTdHJva2VXaWR0aCA9IHRoaXMuYWJzb2x1dGVTdHJva2VXaWR0aCA/PyB0aGlzLmljb25Db25maWcuYWJzb2x1dGVTdHJva2VXaWR0aDtcbiAgICAgIGNvbnN0IG5hbWVPckljb24gPSB0aGlzLmltZyA/PyB0aGlzLm5hbWU7XG4gICAgICBjb25zdCByZXN0QXR0cmlidXRlcyA9IHRoaXMuZ2V0UmVzdEF0dHJpYnV0ZXMoKTtcblxuICAgICAgaWYgKCFoYXNBMTF5UHJvcChyZXN0QXR0cmlidXRlcykpIHtcbiAgICAgICAgdGhpcy5yZW5kZXJlci5zZXRBdHRyaWJ1dGUodGhpcy5lbGVtLm5hdGl2ZUVsZW1lbnQsICdhcmlhLWhpZGRlbicsICd0cnVlJyk7XG4gICAgICB9XG5cbiAgICAgIGlmICh0eXBlb2YgbmFtZU9ySWNvbiA9PT0gJ3N0cmluZycpIHtcbiAgICAgICAgY29uc3QgaWNvT2ZOYW1lID0gdGhpcy5nZXRJY29uKHRoaXMudG9QYXNjYWxDYXNlKG5hbWVPckljb24pKTtcbiAgICAgICAgaWYgKGljb09mTmFtZSkge1xuICAgICAgICAgIHRoaXMucmVwbGFjZUVsZW1lbnQoaWNvT2ZOYW1lKTtcbiAgICAgICAgfSBlbHNlIHtcbiAgICAgICAgICB0aHJvdyBuZXcgRXJyb3IoXG4gICAgICAgICAgICBgVGhlIFwiJHtuYW1lT3JJY29ufVwiIGljb24gaGFzIG5vdCBiZWVuIHByb3ZpZGVkIGJ5IGFueSBhdmFpbGFibGUgaWNvbiBwcm92aWRlcnMuYCxcbiAgICAgICAgICApO1xuICAgICAgICB9XG4gICAgICB9IGVsc2UgaWYgKEFycmF5LmlzQXJyYXkobmFtZU9ySWNvbikpIHtcbiAgICAgICAgdGhpcy5yZXBsYWNlRWxlbWVudChuYW1lT3JJY29uKTtcbiAgICAgIH0gZWxzZSB7XG4gICAgICAgIHRocm93IG5ldyBFcnJvcihgTm8gaWNvbiBuYW1lIG9yIGltYWdlIGhhcyBiZWVuIHByb3ZpZGVkLmApO1xuICAgICAgfVxuICAgIH1cblxuICAgIHRoaXMuY2hhbmdlRGV0ZWN0b3IubWFya0ZvckNoZWNrKCk7XG4gIH1cblxuICByZXBsYWNlRWxlbWVudChpbWc6IEx1Y2lkZUljb25EYXRhKTogdm9pZCB7XG4gICAgY29uc3QgY2hpbGRFbGVtZW50cyA9IHRoaXMuZWxlbS5uYXRpdmVFbGVtZW50LmNoaWxkTm9kZXM7XG5cbiAgICBjb25zdCBhdHRyaWJ1dGVzID0ge1xuICAgICAgLi4uZGVmYXVsdEF0dHJpYnV0ZXMsXG4gICAgICB3aWR0aDogdGhpcy5zaXplLFxuICAgICAgaGVpZ2h0OiB0aGlzLnNpemUsXG4gICAgICBzdHJva2U6IHRoaXMuY29sb3IgPz8gdGhpcy5pY29uQ29uZmlnLmNvbG9yLFxuICAgICAgJ3N0cm9rZS13aWR0aCc6IHRoaXMuYWJzb2x1dGVTdHJva2VXaWR0aFxuICAgICAgICA/IGZvcm1hdEZpeGVkKHRoaXMuc3Ryb2tlV2lkdGggLyAodGhpcy5zaXplIC8gdGhpcy5kZWZhdWx0U2l6ZSkpXG4gICAgICAgIDogdGhpcy5zdHJva2VXaWR0aC50b1N0cmluZygxMCksXG4gICAgfTtcbiAgICBjb25zdCBpY29FbGVtZW50ID0gdGhpcy5jcmVhdGVFbGVtZW50KFsnc3ZnJywgYXR0cmlidXRlcywgaW1nXSk7XG4gICAgaWNvRWxlbWVudC5jbGFzc0xpc3QuYWRkKCdsdWNpZGUnKTtcbiAgICBpZiAodHlwZW9mIHRoaXMubmFtZSA9PT0gJ3N0cmluZycpIHtcbiAgICAgIGljb0VsZW1lbnQuY2xhc3NMaXN0LmFkZChgbHVjaWRlLSR7dGhpcy5uYW1lLnJlcGxhY2UoJ18nLCAnLScpfWApO1xuICAgIH1cblxuICAgIGlmICh0aGlzLmNsYXNzKSB7XG4gICAgICBpY29FbGVtZW50LmNsYXNzTGlzdC5hZGQoXG4gICAgICAgIC4uLnRoaXMuY2xhc3NcbiAgICAgICAgICAuc3BsaXQoLyAvKVxuICAgICAgICAgIC5tYXAoKGEpID0+IGEudHJpbSgpKVxuICAgICAgICAgIC5maWx0ZXIoKGEpID0+IGEubGVuZ3RoID4gMCksXG4gICAgICApO1xuICAgIH1cblxuICAgIGZvciAoY29uc3QgY2hpbGQgb2YgY2hpbGRFbGVtZW50cykge1xuICAgICAgdGhpcy5yZW5kZXJlci5yZW1vdmVDaGlsZCh0aGlzLmVsZW0ubmF0aXZlRWxlbWVudCwgY2hpbGQpO1xuICAgIH1cbiAgICB0aGlzLnJlbmRlcmVyLmFwcGVuZENoaWxkKHRoaXMuZWxlbS5uYXRpdmVFbGVtZW50LCBpY29FbGVtZW50KTtcbiAgfVxuXG4gIGdldFJlc3RBdHRyaWJ1dGVzKCk6IFJlY29yZDxzdHJpbmcsIHN0cmluZz4ge1xuICAgIGNvbnN0IHJlc3RBdHRyaWJ1dGVNYXA6IE5hbWVkTm9kZU1hcCA9IHRoaXMuZWxlbS5uYXRpdmVFbGVtZW50LmF0dHJpYnV0ZXM7XG4gICAgY29uc3QgcmVzdEF0dHJpYnV0ZXMgPSBPYmplY3QuZnJvbUVudHJpZXMoXG4gICAgICBBcnJheS5mcm9tKHJlc3RBdHRyaWJ1dGVNYXApLm1hcCgoaXRlbSkgPT4gW2l0ZW0ubmFtZSwgaXRlbS52YWx1ZV0pLFxuICAgICk7XG4gICAgcmV0dXJuIHJlc3RBdHRyaWJ1dGVzO1xuICB9XG5cbiAgdG9QYXNjYWxDYXNlKHN0cjogc3RyaW5nKTogc3RyaW5nIHtcbiAgICByZXR1cm4gc3RyLnJlcGxhY2UoXG4gICAgICAvKFxcdykoW2EtejAtOV0qKShffC18XFxzKikvZyxcbiAgICAgIChnMCwgZzEsIGcyKSA9PiBnMS50b1VwcGVyQ2FzZSgpICsgZzIudG9Mb3dlckNhc2UoKSxcbiAgICApO1xuICB9XG5cbiAgcHJpdmF0ZSBwYXJzZU51bWJlcih2YWx1ZTogc3RyaW5nIHwgbnVtYmVyKTogbnVtYmVyIHtcbiAgICBpZiAodHlwZW9mIHZhbHVlID09PSAnc3RyaW5nJykge1xuICAgICAgY29uc3QgcGFyc2VkVmFsdWUgPSBwYXJzZUludCh2YWx1ZSwgMTApO1xuICAgICAgaWYgKGlzTmFOKHBhcnNlZFZhbHVlKSkge1xuICAgICAgICB0aHJvdyBuZXcgRXJyb3IoYCR7dmFsdWV9IGlzIG5vdCBudW1lcmljLmApO1xuICAgICAgfVxuICAgICAgcmV0dXJuIHBhcnNlZFZhbHVlO1xuICAgIH1cbiAgICByZXR1cm4gdmFsdWU7XG4gIH1cblxuICBwcml2YXRlIGdldEljb24obmFtZTogc3RyaW5nKTogTHVjaWRlSWNvbkRhdGEgfCBudWxsIHtcbiAgICBmb3IgKGNvbnN0IGljb25Qcm92aWRlciBvZiBBcnJheS5pc0FycmF5KHRoaXMuaWNvblByb3ZpZGVycylcbiAgICAgID8gdGhpcy5pY29uUHJvdmlkZXJzXG4gICAgICA6IFt0aGlzLmljb25Qcm92aWRlcnNdKSB7XG4gICAgICBpZiAoaWNvblByb3ZpZGVyLmhhc0ljb24obmFtZSkpIHtcbiAgICAgICAgcmV0dXJuIGljb25Qcm92aWRlci5nZXRJY29uKG5hbWUpO1xuICAgICAgfVxuICAgIH1cbiAgICByZXR1cm4gbnVsbDtcbiAgfVxuXG4gIHByaXZhdGUgY3JlYXRlRWxlbWVudChbdGFnLCBhdHRycywgY2hpbGRyZW4gPSBbXV06IHJlYWRvbmx5IFtcbiAgICBzdHJpbmcsXG4gICAgU3ZnQXR0cmlidXRlcyxcbiAgICBMdWNpZGVJY29uRGF0YT8sXG4gIF0pIHtcbiAgICBjb25zdCBlbGVtZW50ID0gdGhpcy5yZW5kZXJlci5jcmVhdGVFbGVtZW50KHRhZywgJ2h0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnJyk7XG5cbiAgICBPYmplY3Qua2V5cyhhdHRycykuZm9yRWFjaCgobmFtZSkgPT4ge1xuICAgICAgY29uc3QgYXR0clZhbHVlOiBzdHJpbmcgPVxuICAgICAgICB0eXBlb2YgYXR0cnNbbmFtZV0gPT09ICdzdHJpbmcnID8gKGF0dHJzW25hbWVdIGFzIHN0cmluZykgOiBhdHRyc1tuYW1lXS50b1N0cmluZygxMCk7XG4gICAgICB0aGlzLnJlbmRlcmVyLnNldEF0dHJpYnV0ZShlbGVtZW50LCBuYW1lLCBhdHRyVmFsdWUpO1xuICAgIH0pO1xuXG4gICAgaWYgKGNoaWxkcmVuLmxlbmd0aCkge1xuICAgICAgY2hpbGRyZW4uZm9yRWFjaCgoY2hpbGQpID0+IHtcbiAgICAgICAgY29uc3QgY2hpbGRFbGVtZW50ID0gdGhpcy5jcmVhdGVFbGVtZW50KGNoaWxkKTtcbiAgICAgICAgdGhpcy5yZW5kZXJlci5hcHBlbmRDaGlsZChlbGVtZW50LCBjaGlsZEVsZW1lbnQpO1xuICAgICAgfSk7XG4gICAgfVxuXG4gICAgcmV0dXJuIGVsZW1lbnQ7XG4gIH1cbn1cbiJdfQ==