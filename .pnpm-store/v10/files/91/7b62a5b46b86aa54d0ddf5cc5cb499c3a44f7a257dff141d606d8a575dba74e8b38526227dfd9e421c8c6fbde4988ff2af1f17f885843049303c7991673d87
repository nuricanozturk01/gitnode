import { ChangeDetectorRef, ElementRef, OnChanges, Renderer2, SimpleChange } from '@angular/core';
import { LucideIconData } from '../icons/types';
import { LucideIconProviderInterface } from './lucide-icon.provider';
import { LucideIconConfig } from './lucide-icon.config';
import * as i0 from "@angular/core";
interface TypedChange<T> extends SimpleChange {
    previousValue: T;
    currentValue: T;
}
declare type LucideAngularComponentChanges = {
    name?: TypedChange<string | LucideIconData>;
    img?: TypedChange<LucideIconData | undefined>;
    color?: TypedChange<string>;
    size?: TypedChange<number>;
    strokeWidth?: TypedChange<number>;
    absoluteStrokeWidth?: TypedChange<boolean>;
    class: TypedChange<string>;
};
export declare function formatFixed(number: number, decimals?: number): string;
export declare class LucideAngularComponent implements OnChanges {
    private elem;
    private renderer;
    private changeDetector;
    private iconProviders;
    private iconConfig;
    class?: string;
    name?: string | LucideIconData;
    img?: LucideIconData;
    color?: string;
    absoluteStrokeWidth: boolean;
    defaultSize: number;
    constructor(elem: ElementRef, renderer: Renderer2, changeDetector: ChangeDetectorRef, iconProviders: LucideIconProviderInterface[], iconConfig: LucideIconConfig);
    _size?: number;
    get size(): number;
    set size(value: string | number | undefined);
    _strokeWidth?: number;
    get strokeWidth(): number;
    set strokeWidth(value: string | number | undefined);
    ngOnChanges(changes: LucideAngularComponentChanges): void;
    replaceElement(img: LucideIconData): void;
    getRestAttributes(): Record<string, string>;
    toPascalCase(str: string): string;
    private parseNumber;
    private getIcon;
    private createElement;
    static ɵfac: i0.ɵɵFactoryDeclaration<LucideAngularComponent, never>;
    static ɵcmp: i0.ɵɵComponentDeclaration<LucideAngularComponent, "lucide-angular, lucide-icon, i-lucide, span-lucide", never, { "class": "class"; "name": "name"; "img": "img"; "color": "color"; "absoluteStrokeWidth": "absoluteStrokeWidth"; "size": "size"; "strokeWidth": "strokeWidth"; }, {}, never, ["*"]>;
}
export {};
