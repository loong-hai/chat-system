declare module 'vue-virtual-scroller' {
    import { DefineComponent } from 'vue'

    export const DynamicScroller: DefineComponent<{
        items: any[]
        minItemSize: number
        direction?: 'vertical' | 'horizontal'
        keyField?: string
        listTag?: string
        itemTag?: string
    }>

    export const DynamicScrollerItem: DefineComponent<{
        item: any
        active: boolean
        sizeDependencies?: any[]
        tag?: string
    }>

    export const RecycleScroller: DefineComponent<{
        items: any[]
        itemSize: number
        direction?: 'vertical' | 'horizontal'
        keyField?: string
        listTag?: string
        itemTag?: string
        buffer?: number
    }>
}