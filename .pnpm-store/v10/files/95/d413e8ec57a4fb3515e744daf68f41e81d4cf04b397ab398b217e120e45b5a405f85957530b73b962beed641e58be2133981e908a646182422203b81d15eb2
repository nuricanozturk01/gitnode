import { LucideIconData, LucideIcons } from '../icons/types';
import { InjectionToken } from '@angular/core';
export interface LucideIconProviderInterface {
    hasIcon(name: string): boolean;
    getIcon(name: string): LucideIconData | null;
}
export declare const LUCIDE_ICONS: InjectionToken<LucideIconProviderInterface>;
export declare class LucideIconProvider implements LucideIconProviderInterface {
    private icons;
    constructor(icons: LucideIcons);
    getIcon(name: string): LucideIconData | null;
    hasIcon(name: string): boolean;
}
